# Runner Tracker — Deployment Guide

Complete instructions for deploying the Runner Tracker on any platform.

## Table of Contents

1. [Server Setup](#1-server-setup)
2. [Running Without Keeping the Window Open](#2-running-without-keeping-the-window-open)
3. [Making It Public](#3-making-it-public)
4. [Android App](#4-android-app)
5. [iOS App](#5-ios-app)
6. [BLE Proximity Tags](#6-ble-proximity-tags)
7. [Race Day Checklist](#7-race-day-checklist)
8. [Troubleshooting](#8-troubleshooting)
9. [Production Deploy (VPS)](#9-production-deploy-vps)

---

## 1. Server Setup

The server is a single Python 3 file with zero external dependencies.

### Requirements

- Python 3.8+ (comes pre-installed on macOS, most Linux distros)
- Any machine with an internet connection: Mac, Linux, Windows, Raspberry Pi, VPS

### Start the Server

```bash
cd runner-tracker
python3 server.py
```

You'll see:
```
Runner tracker live: http://0.0.0.0:8090/
  Control room:  http://0.0.0.0:8090/
  Runner phone:  http://0.0.0.0:8090/tracker?name=JIM
  Course overlay: drop a course.gpx next to this file
```

**Leave this terminal open** — or see [Section 2](#2-running-without-keeping-the-window-open) to run it without keeping a window open.

### Custom Port

```bash
python3 server.py --port 8080
```

### Optional: Course Overlay

Draw your race route using [GPX Studio](https://gpx.studio) or [TrailLink](https://www.traillink.org), export as `.gpx`, and drop it as `course.gpx` in the same folder as `server.py`. The map will automatically show the course overlay.

---

## 2. Running Without Keeping the Window Open (And Surviving Reboots)

Right now, if you close the terminal window or restart your computer, the runner-tracker stops working. This section shows you how to set it up so it runs automatically, all the time, forever.

**What we're doing:** We're creating two things:
1. A startup script — a file that starts everything for you
2. A watchdog — a background process that checks every 5 minutes to make sure everything is still running, and restarts it if something broke

You only need to do this once.

---

### Step 1: Open your terminal

Press **Ctrl + Alt + T** on your keyboard. A black window should appear. This is your terminal.

---

### Step 2: Create the startup script folder

Copy and paste this command into the terminal, then press Enter:

```bash
mkdir -p ~/bin
```

**What this does:** Creates a folder called `bin` inside your home folder. This is where we'll put our startup script.

---

### Step 3: Create the startup script

Copy and paste this entire block into the terminal, then press Enter:

```bash
cat > ~/bin/start-runner-tracker.sh << 'ENDOFSCRIPT'
#!/bin/bash

# Stop any old server that might be running
pkill -f "server.py.*8090" 2>/dev/null
sleep 1

# Start the runner-tracker server
cd ~/runner-tracker
nohup python3 server.py --port 8090 > /tmp/runner-tracker.log 2>&1 &
disown

# Stop any old tunnel that might be running
pkill -f "cloudflared" 2>/dev/null
sleep 2

# Start the Cloudflare tunnel
nohup cloudflared tunnel --url http://localhost:8090 > /tmp/cloudflared.log 2>&1 &
disown
ENDOFSCRIPT
```

**What this does:** Creates a file called `start-runner-tracker.sh` in your `~/bin` folder. This file contains all the commands needed to start the server and tunnel.

---

### Step 4: Make the script executable

Copy and paste this into the terminal, then press Enter:

```bash
chmod +x ~/bin/start-runner-tracker.sh
```

**What this does:** Tells your computer this file is a program that's allowed to run.

---

### Step 5: Add it to your startup

Copy and paste this into the terminal, then press Enter:

```bash
echo '
# Start runner-tracker on login
~/bin/start-runner-tracker.sh
' >> ~/.bashrc
```

**What this does:** Every time you open a new terminal window, it will automatically run the startup script. This means the server starts whenever you use your computer.

---

### Step 6: Create the watchdog script

Copy and paste this entire block into the terminal, then press Enter:

```bash
cat > ~/runner-tracker/health-check.sh << 'ENDOFSCRIPT'
#!/bin/bash
LOG="/tmp/health-check.log"
DATE=$(date '+%Y-%m-%d %H:%M:%S')

# Check if the server is running (port 8090)
if ! ss -tlnp | grep -q ":8090 "; then
    echo "[$DATE] runner-tracker down, restarting..." >> "$LOG"
    sleep 1
    pkill -f "server.py.*8090" 2>/dev/null
    sleep 1
    cd ~/runner-tracker && nohup python3 server.py --port 8090 > /tmp/runner-tracker.log 2>&1 &
    disown
fi

# Check if the tunnel is running
if ! pgrep -x cloudflared > /dev/null; then
    echo "[$DATE] cloudflared tunnel down, restarting..." >> "$LOG"
    sleep 2
    nohup cloudflared tunnel --url http://localhost:8090 > /tmp/cloudflared.log 2>&1 &
    disown
fi
ENDOFSCRIPT
```

**What this does:** Creates a file called `health-check.sh` in your `~/runner-tracker` folder. This is the watchdog — it checks if the server and tunnel are still running and restarts them if they've stopped.

---

### Step 7: Make the watchdog executable

Copy and paste this into the terminal, then press Enter:

```bash
chmod +x ~/runner-tracker/health-check.sh
```

---

### Step 8: Set the watchdog to run every 5 minutes

Copy and paste this into the terminal, then press Enter:

```bash
(crontab -l 2>/dev/null; echo "*/5 * * * * ~/runner-tracker/health-check.sh") | crontab -
```

**What this does:** Tells your computer to run the watchdog script every 5 minutes, automatically. If the server or tunnel crashes or stops, the watchdog restarts it within 5 minutes.

To verify this was added correctly, you can type `crontab -l` and press Enter. You should see a line that says:
```
*/5 * * * * ~/runner-tracker/health-check.sh
```

---

### Step 9: Test it

Run the startup script manually for the first time:

```bash
~/bin/start-runner-tracker.sh
```

Wait about 10 seconds, then open your web browser and go to:

```
http://localhost:8090
```

You should see the runner-tracker page.

**If you see the page:** Everything is working. You can close the terminal window now — the server will keep running.

**If you don't see the page:** Check the logs by typing:
```
cat /tmp/runner-tracker.log
```

Paste the output here if you need help.

---

### Step 10: Test that it survives a restart

Restart your computer. After it boots back up and you're logged in, open your web browser and go to:

```
http://localhost:8090
```

You should see the runner-tracker page again. It started automatically — no terminal window needed.

---

### Summary of what you just did

| What | What it does |
|------|-------------|
| Startup script (`~/bin/start-runner-tracker.sh`) | Starts the server and tunnel when you open a terminal |
| Watchdog (`~/runner-tracker/health-check.sh`) | Checks every 5 minutes and restarts everything if it stopped |
| Crontab entry | Runs the watchdog automatically every 5 minutes |
| `.bashrc` entry | Runs the startup script every time you open a terminal |

**You're done.** The server and tunnel will run all the time, survive crashes, survive reboots, and need no terminal windows. If anything ever stops working, the watchdog will fix it within 5 minutes.

---

## 3. Making It Public

Runners on cellular need to reach your server. Here are your options:

### Option A: Cloudflare Tunnel (Free, Easiest)

Cloudflare Tunnel creates a secure, temporary public URL — no domain, no server config, no account needed for basic use. But a private domain for about $11.00 a year is highly recomended because the URL will change with the free temporary public URL created by Cloudflare. The temporary public URL is good for testing.

#### Install cloudflared

**macOS:**
```bash
brew install cloudflared
```

**Linux (Debian/Ubuntu):**
```bash
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb -o cloudflared.deb
sudo dpkg -i cloudflared.deb
```

**Windows:**
Download from https://github.com/cloudflare/cloudflared/releases and add to PATH.

#### Start the Tunnel

Open a **second terminal** (keep the server running in the first):  (A second terminal will not have to be kept open if you use your own domain.

```bash
cd runner-tracker
cloudflared tunnel --url http://localhost:8090
```

After ~5 seconds, you'll see output like:
```
Your quick tunnel has been created! Visit it at:
https://random-words-here.trycloudflare.com
```

**Copy this URL** — this is what you share with runners. (The QR code can be used to quickly link your runners to the site)

### Option B: Tailscale Funnel (Free, Requires Tailscale)

If you and your volunteers use Tailscale:

```bash
tailscale funnel 8090
```

This publishes your server on a `*.ts.net` URL accessible from anywhere.

### Option C: Public IP / Port Forwarding

If your machine has a public IP:

```bash
python3 server.py --host 0.0.0.0
```

Forward port 8090 in your router, then share `http://YOUR-PUBLIC-IP:8090`.

### Option D: VPS (Production)

See [Section 9](#9-production-deploy-vps) below.

---

## 4. Android App

### Quick Install (Pre-built APK)

A debug APK is included for immediate testing:

1. Copy `app/build/outputs/apk/debug/app-debug.apk` to your phone
2. Tap to install (allow "unknown sources" if prompted)
3. Open the app, enter your bib number + server URL
4. Tap **START TRACKING**

### Share APK with Runners

1. Copy `app-debug.apk` to the `static/` folder
2. Access it via your public URL: `https://YOUR-TUNNEL-URL/app-debug.apk`
3. Or email it, share via messaging apps

### Build from Source (Optional)

**On Linux:**
```bash
export ANDROID_HOME=~/android-sdk
export GRADLE_HOME=~/gradle/gradle-8.5
export PATH=$PATH:$GRADLE_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
cd runner-tracker
gradle assembleDebug
```

**On macOS or Windows:** Use Android Studio:
1. Open Android Studio → File → Open → select the `runner-tracker` folder
2. Wait for Gradle sync
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK is at `app/build/outputs/apk/debug/app-debug.apk`

### Runner Instructions (Copy and Paste)

```
RACE TRACKING SETUP

1. Download and install the Runner Tracker app:
   [paste APK download link]

2. Open the app and enter:
   - Your bib number (e.g., 142)
   - Server URL: https://YOUR-TUNNEL-URL.trycloudflare.com

3. Tap "START TRACKING"

4. Allow location permission + battery optimization exemption

5. Put your phone in your pocket — tracking continues automatically

Your progress will appear live on the race map!
```

---

## 5. iPhone Users

iPhone users have **three options**. The browser tracker works immediately; Shortcuts gives background tracking; the native iOS app is included but untested.

### Option A — iOS Shortcuts (Recommended for Background Tracking)

Every iPhone has the **Shortcuts** app built in. Runners can create a single shortcut that POSTs their GPS coordinates to your server automatically — background tracking, no screen needed.

**Step 1:** Create the shortcut
1. Open the **Shortcuts** app (pre-installed on every iPhone)
2. Tap **+** (top right) to create a new shortcut
3. Tap **Add Action** → search for **"Get Current Location"** → tap it
4. Tap **Add Action** → search for **"HTTP Request"** (or "URL" → "Request URL" depending on iOS version)
5. Configure the HTTP request:
   - **Method:** `POST`
   - **URL:** `https://YOUR-TUNNEL-URL.trycloudflare.com/api/ping`
   - **Body Type:** `JSON`
   - **Body:** paste this (replace YOURBIB):
```json
{"name": "YOURBIB", "lat": {{Location Latitude}}, "lng": {{Location Longitude}}}
```
6. Rename the shortcut to something like "Runner Tracker"
7. In Shortcuts settings, enable **"Run in Background"** (if available)
8. Optional: Add to Home Screen for one-tap access

**Step 2:** Set up background execution
- In Shortcuts, find your shortcut → tap **⋯** → toggle on **"Run Immediately"**
- Create an Automation (Shortcuts → Automate tab) to run every 20 minutes in the background
- iOS will keep posting GPS as long as the shortcut stays enabled

**Note:** iOS background execution is limited — Shortcuts cannot run truly in the background like a native app. But it's much better than the browser option (screen can stay off between runs). Test it before race day.

### Option B — Browser Tracker (No App Install)

1. Open Safari on the iPhone
2. Go to: `https://YOUR-TUNNEL-URL.trycloudflare.com/tracker`
3. Enter your bib number
4. Tap "START TRACKING"
5. Allow location permission
6. **Keep the screen ON the entire race** (background tracking not available in browser)

### Option C — Native iOS App (Requires Mac + Xcode)

The iOS app source is included but **has never been tested**. Building it requires a Mac with Xcode and an Apple Developer account ($99/year) for TestFlight, or debug builds that expire after 7 days.

### Option D — Overlander (Unverified — user-reported)

[Overlander](https://apps.apple.com/us/app/overlander/id6758252722) is a free iOS app. One user has reported it works with Runner Tracker. This has **not been verified** — if you try it, please report back.

Runner Instructions (Copy and Paste):

```
RACE TRACKING SETUP (iPhone)

Option 1 — Shortcuts (background tracking, no install):
1. Open the built-in "Shortcuts" app on your iPhone
2. Follow the setup instructions from the race organizer
3. The shortcut sends your GPS to the race server automatically
4. Screen can stay off between updates

Option 2 — Browser (no app install):
1. Open Safari and go to: https://YOUR-TUNNEL-URL.trycloudflare.com/tracker
2. Enter your bib number and tap START TRACKING
3. Allow location permission
4. Keep the screen ON the entire race

Option 3 — Overlander (unverified):
1. Install Overlander from the App Store (free)
2. Ask the organizer if it's configured for this event
```

---

## 6. BLE Proximity Tags

### Hardware

|       Item          |    Cost    |        Where          |
|--------------------|-------------|-----------------------|
| DX-CP27 BLE beacon | ~$5 each    | Amazon, AliExpress    |
| CR2032 battery     | ~$0.50 each | Any electronics store |

### Setup

1. **Program each tag** with nRF Connect (Android) or the manufacturer's app:
   - Set a unique iBeacon UUID for your race
   - Use the major number as the bib number
   - Broadcast interval: 1-5 seconds (default is fine)

2. **Create the tag map** (`tagmap.json`):
```json
{
  "IB:00112233-4455-6677-8899-aabbccddeeff:1:1": "Bib 001",
  "IB:00112233-4455-6677-8899-aabbccddeeff:1:2": "Bib 002",
  "IB:00112233-4455-6677-8899-aabbccddeeff:1:142": "Bib 142"
}
```

3. **Distribute to Android runners** — paste the JSON into the app's tag map field

### How It Works

```
Runner A (Bib 142) passes within ~10-30m of Runner B (Bib 008)
  ↓
Phone A detects tag B
  ↓
App sends: "Bib 142 saw Bib 008 at (lat, lng) at 14:32:05"
  ↓
Server logs proximity event
  ↓
Organizer sees relative positions on the map
```

### Tag Configuration Tips

- Use a **unique UUID** for your race (generates at random UUID generators)
- Use the **major number** as the bib number (easy to match)
- Set **minor** to 0 or 1
- **Battery life:** 6-12 months with default settings
- **Range:** 10-50m depending on environment

---

## 7. Race Day Checklist

- [ ] Server is running (`python3 server.py`) — Terminal 1
- [ ] Tunnel is running (`cloudflared tunnel --url http://localhost:8090`) — Terminal 2
- [ ] Public URL copied and shared with runners (at least 1 hour before)
- [ ] Runners have installed the app or have the browser link
- [ ] Runners have entered their bib number
- [ ] Runners have allowed location permission
- [ ] (Optional) `course.gpx` is in the folder for the map overlay
- [ ] (Optional) `tagmap.json` is created for BLE proximity
- [ ] Laptop is plugged in and connected to the internet
- [ ] Test: open the map in your phone browser, start tracking, verify marker appears

---

## 8. Troubleshooting

| Problem | Solution |
|---|---|
| **Phone shows a search page** instead of the tracker | Paste the URL into the browser's ADDRESS BAR, not the search box |
| **502 Bad Gateway** | The server or tunnel crashed. Restart both terminals. |
| **Runner doesn't appear on the map** | Make sure they tapped "START TRACKING" AND allowed location. They appear within 20 seconds. |
| **"Acquiring GPS" forever** | GPS needs a clear view of the sky. Runners need to be outside. |
| **Phone battery drains fast** | The tracker pings every 20s. Tell runners to avoid other apps while tracking. |
| **Android app stops after screen off** | Settings → Apps → Runner Tracker → Battery → "Unrestricted" |
| **Tunnel disconnects during race** | Free tunnels can occasionally drop. Restart the tunnel terminal. |
| **BLE tags not detected** | Check the tagmap matches actual tag IDs. Use nRF Connect to verify. Ensure Bluetooth is ON. |
| **iOS runners can't detect BLE tags** | iOS doesn't support background BLE scanning. They get GPS tracking only. |

---

## 9. Production Deploy (VPS)

For multi-race use or when you don't want your laptop running:

### Using a $5/mo VPS (DigitalOcean, Hetzner, Oracle Free Tier)

```bash
# SSH into your VPS
ssh root@your-vps-ip

# Install Python (if not present)
apt update && apt install -y python3 python3-pip

# Clone or copy the runner-tracker folder
mkdir -p /opt/runner-tracker
# Copy files via scp or git clone

# Install systemd service
cat > /etc/systemd/system/runner-tracker.service << 'EOF'
[Unit]
Description=Runner Tracker Server
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/runner-tracker
ExecStart=/usr/bin/python3 /opt/runner-tracker/server.py --host 127.0.0.1 --port 8090
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# Enable and start
systemctl daemon-reload
systemctl enable runner-tracker
systemctl start runner-tracker

# Verify
systemctl status runner-tracker
curl http://localhost:8090/api/runners
```

### Add Cloudflare Tunnel (Permanent URL)

```bash
# Install cloudflared on the VPS
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb -o cloudflared.deb
dpkg -i cloudflared.deb

# Login to Cloudflare (first time only — free account needed)
cloudflared tunnel login

# Create a named tunnel
cloudflared tunnel create runner-tracker

# Configure the tunnel
cat > /etc/cloudflared/config.yml << 'EOF'
tunnel: YOUR-TUNNEL-ID
credentials-file: /etc/cloudflared/YOUR-TUNNEL-ID.json

ingress:
  - hostname: tracker.yourdomain.com
    service: http://localhost:8090
  - service: http_status:404
EOF

# Add DNS record in Cloudflare dashboard for tracker.yourdomain.com
# Point CNAME to YOUR-TUNNEL-ID.trycloudflare.com

# Start the tunnel as a service
cat > /etc/systemd/system/cloudflared.service << 'EOF'
[Unit]
Description=Cloudflare Tunnel
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/cloudflared --config /etc/cloudflared/config.yml tunnel run
Restart=always

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable cloudflared
systemctl start cloudflared
```

### Add Basic Auth (Optional — Protect the Control Room)

Add this to `server.py` before the `Handler` class:

```python
import base64
from http.server import HTTPStatus

AUTH_USER = "admin"
AUTH_PASS = "change-this-password"

def check_auth(handler):
    auth_header = handler.headers.get("Authorization", "")
    if auth_header.startswith("Basic "):
        decoded = base64.b64decode(auth_header[6:]).decode()
        user, _, password = decoded.partition(":")
        return user == AUTH_USER and password == AUTH_PASS
    return False
```

Then wrap the GET handlers with:
```python
if not check_auth(self):
    self.send_response(401)
    self.send_header("WWW-Authenticate", 'Basic realm="Control Room"')
    self.end_headers()
    return
```

---

## License

MIT — deploy this for your race, your club, your event.
