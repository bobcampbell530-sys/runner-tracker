#!/usr/bin/env python3
"""
runner-tracker: live wilderness race tracking server.

Zero dependencies (Python 3.8+ stdlib only). Run with:
    python3 server.py [--host 0.0.0.0] [--port 8080]

Endpoints:
    GET  /                     Control room live map
    GET  /tracker?name=NAME    Runner phone page (GPS ping uploader)
    GET  /api/runners          JSON snapshot of all runners
    POST /api/ping             Runner GPS ping   {"name","lat","lng","alt","acc","speed","ts"}
    POST /api/checkpoint       RFID/manual scan  {"name","station","ts"}
    GET  /api/checkpoints      All checkpoint events
    GET  /course.gpx           Course overlay (if course.gpx sits next to this file)
"""
import argparse
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

BASE = os.path.dirname(os.path.abspath(__file__))

# ---------------- in-memory state ----------------
lock = threading.Lock()
runners = {}       # name -> dict(name, last, history=[...], last_seen)
checkpoints = []   # list of {name, station, ts}
proximity = []     # list of {seen_by, seen, lat, lng, rssi, ts} — BLE tag detections
CHECKPOINT_LIMIT = 50000       # max checkpoint events in memory
HISTORY_LIMIT = 10000          # GPS points kept per runner (memory guard)
RUNNER_TTL = 6 * 3600          # drop runner if silent for 6h (finished/DNF)
PROXIMITY_LIMIT = 10000        # max proximity events in memory


def ping(name, lat, lng, alt=None, acc=None, speed=None, ts=None):
    ts = ts or time.time()
    with lock:
        r = runners.get(name)
        if r is None:
            r = {"name": name, "last": None, "history": []}
            runners[name] = r
        r["last"] = {"lat": lat, "lng": lng, "alt": alt,
                     "acc": acc, "speed": speed, "ts": ts}
        r["history"].append({"lat": lat, "lng": lng, "ts": ts})
        if len(r["history"]) > HISTORY_LIMIT:
            r["history"] = r["history"][-HISTORY_LIMIT:]
        r["last_seen"] = time.time()
        # prune stale runners
        now = time.time()
        for k in [k for k, v in runners.items()
                  if now - v.get("last_seen", 0) > RUNNER_TTL]:
            del runners[k]
        online = len(runners)
    return online   # runner count; snapshot() acquires the lock itself


def add_checkpoint(name, station, ts=None):
    ev = {"name": name, "station": station, "ts": ts or time.time()}
    with lock:
        checkpoints.append(ev)
        if len(checkpoints) > CHECKPOINT_LIMIT:
            del checkpoints[:CHECKPOINT_LIMIT // 2]
    return ev


def add_proximity(seen_by, seen, lat=None, lng=None, rssi=None, ts=None):
    """Record that runner 'seen_by' detected BLE tag of runner 'seen'."""
    ev = {
        "seen_by": seen_by,
        "seen": seen,
        "lat": lat,
        "lng": lng,
        "rssi": rssi,
        "ts": ts or time.time(),
    }
    with lock:
        proximity.append(ev)
        if len(proximity) > PROXIMITY_LIMIT:
            del proximity[:PROXIMITY_LIMIT // 2]
    return ev


def snapshot():
    with lock:
        return {
            "runners": [
                {"name": v["name"], "last": v["last"],
                 "track": v["history"][-2000:]}   # cap for transport
                for v in runners.values()
            ],
            "checkpoints": list(checkpoints[-2000:]),
            "proximity": list(proximity[-2000:]),
        }


# ---------------- HTTP ----------------
class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"[{time.strftime('%H:%M:%S')}] {fmt % args}", flush=True)

    def _send(self, code, body, ctype="application/json", head_only=False):
        if isinstance(body, (dict, list)):
            body = json.dumps(body)
        if isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if not head_only:
            self.wfile.write(body)
            self.wfile.flush()

    def _body_json(self):
        n = int(self.headers.get("Content-Length", 0))
        return json.loads(self.rfile.read(n) or b"{}")

    def do_HEAD(self):
        self.do_GET(head_only=True)

    def do_OPTIONS(self):
        self._send(204, b"")

    def do_DELETE(self):
        u = urlparse(self.path)
        try:
            if u.path == "/api/runners":
                global runners, checkpoints, proximity
                with lock:
                    runners.clear()
                    checkpoints.clear()
                    proximity.clear()
                print("RESET: all data cleared", flush=True)
                return self._send(200, {"ok": True})
            return self._send(404, {"error": "not found"})
        except Exception as e:
            print(f"DELETE ERROR: {e}", flush=True)
            return self._send(500, {"error": str(e)})

    def do_GET(self, head_only=False):
        u = urlparse(self.path)
        p = u.path
        if p in ("/", "/index.html", "/map.html"):
            return self._file("static/map.html", "text/html", head_only=head_only)
        if p == "/tracker" or p == "/tracker.html":
            return self._file("static/tracker.html", "text/html", head_only=head_only)
        if p == "/api/runners":
            return self._send(200, snapshot())
        if p == "/api/checkpoints":
            with lock:
                return self._send(200, {"checkpoints": checkpoints[-5000:]})
        if p == "/api/proximity":
            with lock:
                return self._send(200, {"proximity": proximity[-5000:]})
        if p == "/course.gpx":
            return self._file("course.gpx", "application/gpx+xml", head_only=head_only)
        return self._send(404, {"error": "not found"})

    def do_POST(self):
        u = urlparse(self.path)
        try:
            data = self._body_json()
        except Exception:
            return self._send(400, {"error": "bad json"})

        if u.path == "/api/ping":
            try:
                name = str(data["name"]).strip()
                lat, lng = float(data["lat"]), float(data["lng"])
            except (KeyError, ValueError, TypeError):
                return self._send(400, {"error": "need name, lat, lng"})
            if not (-90 <= lat <= 90 and -180 <= lng <= 180):
                return self._send(400, {"error": "lat/lng out of range"})
            f = lambda k, dflt: data.get(k, dflt)
            try:
                online = ping(name, lat, lng,
                              alt=float(f("alt", 0) or 0),
                              acc=float(f("acc", 0) or 0),
                              speed=float(f("speed", 0) or 0),
                              ts=float(f("ts", time.time()) or time.time()))
            except (ValueError, TypeError):
                online = ping(name, lat, lng)
            self.log_message("PING %s @ (%.5f, %.5f)", name, lat, lng)
            return self._send(200, {"ok": True, "runners_online": online})

        if u.path == "/api/checkpoint":
            try:
                name = str(data["name"]).strip()
                station = str(data.get("station", "unknown")).strip()
            except Exception:
                return self._send(400, {"error": "need name"})
            ev = add_checkpoint(name, station,
                                data.get("ts"))
            self.log_message("CHECKPOINT %s @ %s", name, station)
            return self._send(200, {"ok": True, "event": ev})

        if u.path == "/api/proximity":
            try:
                seen_by = str(data["seen_by"]).strip()
                seen = str(data["seen"]).strip()
            except (KeyError, ValueError, TypeError):
                return self._send(400, {"error": "need seen_by, seen"})
            ev = add_proximity(
                seen_by, seen,
                lat=float(data["lat"]) if data.get("lat") else None,
                lng=float(data["lng"]) if data.get("lng") else None,
                rssi=int(data["rssi"]) if data.get("rssi") else None,
                ts=float(data["ts"]) if data.get("ts") else None,
            )
            self.log_message("PROXIMITY %s saw %s @ (%s, %s)", seen_by, seen,
                             ev.get("lat"), ev.get("lng"))
            return self._send(200, {"ok": True, "event": ev})

        return self._send(404, {"error": "not found"})

    def _file(self, relpath, ctype, head_only=False):
        path = os.path.join(BASE, relpath)
        if not os.path.isfile(path):
            return self._send(404, {"error": f"{relpath} not found (optional)"}, head_only=head_only)
        with open(path, "rb") as f:
            self._send(200, f.read(), ctype, head_only=head_only)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8090,
                    help="default 8090 (8080 is taken by the runners-* docker stack)")
    args = ap.parse_args()
    srv = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Runner tracker live: http://{args.host}:{args.port}/")
    print(f"  Control room:  http://{args.host}:{args.port}/")
    print(f"  Runner phone:  http://{args.host}:{args.port}/tracker?name=JIM")
    print(f"  Course overlay: drop a course.gpx in {BASE} (auto-loaded)")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.")


if __name__ == "__main__":
    main()
