# Runner Tracker

Live GPS tracking for trail races, fun runs, and outdoor events. Runners start their phones and their positions appear on a live map — no app store, no sign-up, no infrastructure needed.

## How It Works

```
Runners' phones ──GPS pings──▶ Server ──live map──▶ Race organizer
         │                            │
         └──BLE tags──proximity───────┘
```

**GPS Tracking:** Each runner's phone sends GPS coordinates every 20 seconds to the server. The organizer watches a live map showing all runners in real-time.

**BLE Proximity (Android only):** Each runner wears a small BLE beacon tag. Android phones scan for other runners' tags, logging proximity events like "Bib 142 saw Bib 008 at checkpoint 3." No fixed checkpoint infrastructure needed — every runner's phone becomes a sensor.

## What's Inside

| File | What it does |
|---|---|
| `server.py` | The race tracker server (GPS pings + proximity events) |
| `static/map.html` | Control room — live map of all runners |
| `static/tracker.html` | Browser fallback for runners (no app install) |
| `app/` | Android app — background GPS + BLE proximity scanner |
| `ios/` | iOS app — background GPS tracking (Xcode project, **untested**) |
| `tagmap.json.example` | Template for mapping BLE tag IDs to bib numbers |
| `DEPLOY.md` | Complete deployment guide for any platform |

## Quick Start

The fastest way to get running is the **browser mode** — no app install needed:

```bash
# Terminal 1 — start the server
python3 server.py

# Terminal 2 — make it public with Cloudflare Tunnel
cloudflared tunnel --url http://localhost:8090
```

Share the Cloudflare URL with runners:
```
https://your-name.trycloudflare.com/tracker?name=BIB142
```

Runners open the URL in their phone browser, enter their bib number, tap START, and their position appears on the map within 20 seconds.

See [DEPLOY.md](DEPLOY.md) for full instructions on:
- Setting up the server on any machine (Mac, Linux, Windows, Raspberry Pi, VPS)
- Creating a Cloudflare Tunnel (free, no account needed for temporary URLs)
- Building the Android APK
- Building the iOS app (requires Mac + Xcode)
- Setting up BLE proximity tracking

## Deployment Options

| Method | Difficulty | Background Tracking | Best For |
|---|---|---|---|
| **Browser** | Trivial | ❌ Screen must stay on | Quick tests, iPhone users |
| **Android App** | Easy | ✅ Foreground service | Full feature set |
| **iOS App** | Medium | ✅ "Always" location | Requires Mac + Xcode (**untested source**) |
| **Shortcuts** | Easy | ⚠️ Limited background | Built into every iPhone, no install needed |
| **VPS + Tunnel** | Easy | ✅ Any of the above | Permanent or multi-race use |

## Server

The server is a single Python 3 file with zero dependencies. Run it anywhere Python 3 runs:

```bash
python3 server.py --host 0.0.0.0 --port 8090
```

**Endpoints:**
| Endpoint | Method | Description |
|---|---|---|
| `/` | GET | Control room live map |
| `/tracker?name=BIB` | GET | Runner phone page (GPS ping uploader) |
| `/api/runners` | GET | JSON snapshot of all runners |
| `/api/ping` | POST | GPS position update |
| `/api/checkpoint` | POST | Manual/RFID checkpoint scan |
| `/api/proximity` | POST | BLE tag detection event |
| `/api/checkpoints` | GET | All checkpoint events |
| `/api/proximity` | GET | All proximity events |
| `/course.gpx` | GET | Course overlay (drop a .gpx file next to server.py) |
| `DELETE /api/runners` | DELETE | Clear all data |

## Architecture

- **Data is in-memory only** — restart the server and all race data is lost. This is intentional (privacy, simplicity). Add a database for persistence.
- **No authentication** — anyone with the URL can see the map. Add basic auth if you need privacy.
- **Auto-pruning** — runners are removed after 6 hours of silence.
- **Memory guards** — GPS history capped at 10,000 points per runner, 50,000 checkpoint events, 10,000 proximity events.

## Android App

Built with Kotlin, Android SDK 34, Gradle. Features:
- Background GPS tracking (foreground service)
- BLE proximity scanning (detects other runners' beacon tags)
- QR code scanning for auto-filling server URL and bib number
- Persistent notification
- Battery optimization exemption

**Pre-built APK:** A debug APK is included for quick testing. For production, sign with a release key.

**Build from source:**
```bash
export ANDROID_HOME=~/android-sdk
export GRADLE_HOME=~/gradle/gradle-8.5
export PATH=$PATH:$GRADLE_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
gradle assembleDebug
```

## iOS App

Built with Swift and SwiftUI. Features:
- Background GPS tracking (Core Location "Always" permission)
- QR code scanning for auto-filling server URL and bib number
- Deep link support (`runnertracker://setup?url=...&bib=...`)

**Requires:** macOS + Xcode. Build and distribute via TestFlight (free) or direct USB install.

## BLE Proximity Tracking

### Hardware

Small BLE beacon tags worn by each runner (~$3-5 each):
- **DX-CP27** recommended — iBeacon + Eddystone, 50-70m range, CR2032 battery
- Any iBeacon or Eddystone beacon works
- Program with nRF Connect (Android) or manufacturer's app

### Setup

1. Program each tag with a unique identity (UUID + major + minor for iBeacon)
2. Create a `tagmap.json` mapping each tag ID to a bib number
3. Distribute the tagmap JSON to Android runners' apps
4. The app automatically detects other runners' tags and logs proximity events

See [README-BLE.md](README-BLE.md) for a complete BLE proximity guide.

## API Reference

### GPS Ping (`POST /api/ping`)
```json
{
  "name": "Bib 142",
  "lat": 46.2,
  "lng": -121.5,
  "alt": 1200,
  "acc": 8.5,
  "speed": 5.2,
  "ts": 1756000000.0
}
```

### Proximity Event (`POST /api/proximity`)
```json
{
  "seen_by": "Bib 142",
  "seen": "Bib 008",
  "lat": 46.2,
  "lng": -121.5,
  "rssi": -55,
  "ts": 1756000000.0
}
```

### Runner Snapshot (`GET /api/runners`)
```json
{
  "runners": [
    {
      "name": "Bib 142",
      "last": { "lat": 46.2, "lng": -121.5, "alt": 1200, "acc": 8.5, "speed": 5.2, "ts": 1756000000.0 },
      "track": [{ "lat": 46.2, "lng": -121.5, "ts": 1756000000.0 }, ...]
    }
  ],
  "checkpoints": [...],
  "proximity": [...]
}
```

## Limitations

- **No persistence** — data is lost when the server restarts
- **No auth** — the map is public to anyone with the URL
- **Browser battery drain** — the browser tracker needs the screen on; native apps are much more efficient
- **BLE on iOS** — iOS cannot do background BLE scanning; iOS runners get GPS only
- **Single server** — this is designed for a single race event; for multi-race or tournament use, you'd need database persistence and a proper auth system

## Customization

- **Ping interval:** Change `INTERVAL` in `static/tracker.html` (browser) or `TrackingService.kt` / `TrackingManager.swift` (apps). Default: 20 seconds.
- **Server port:** Default 8090, configurable with `--port`
- **Course overlay:** Drop a `.gpx` file named `course.gpx` next to `server.py`
- **Map center:** Edit `static/map.html` to set the initial map center coordinates
- **Runner TTL:** Default 6 hours (`RUNNER_TTL` in server.py)

## License

MIT — use it for your race, your club, your event. No attribution required but appreciated.
