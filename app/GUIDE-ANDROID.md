# Runner Tracker — Android App Install Guide

This guide walks you through installing the Runner Tracker app on your Android phone. **No building or programming needed** — a pre-built APK is included.

---

## WHAT YOU ARE INSTALLING

A phone app that runners install. They enter their bib number, tap START, and their GPS position appears on your live map — **even when the phone is in their pocket with the screen off**.

---

## STEP 1: Get the APK File

The app comes as a single file called `app-debug.apk`. It's included in the project folder.

**Find the file:**
- Open the `runner-tracker` folder on your computer
- Go to: `app → build → outputs → apk → debug`
- You'll see a file called `app-debug.apk` (it's about 5.5 MB)

If you need to rebuild it, see the [README](../README.md) for build instructions.

---

## STEP 2: Transfer the APK to Your Phone

You have several options:

**Option A — USB cable (easiest):**
1. Connect your Android phone to your computer with a USB cable
2. On your phone, when it asks what USB mode to use, choose **"File transfer"** or **"MTP"**
3. On your computer, open your phone's storage (it will appear like a USB drive)
4. Copy `app-debug.apk` to your phone's **Downloads** folder

**Option B — Email:**
1. Open your email
2. Attach `app-debug.apk` to a new email
3. Send it to yourself
4. On your phone, open the email and tap the attachment

**Option C — Cloudflare URL:**
1. Start the server and tunnel (see [DEPLOY.md](../DEPLOY.md))
2. Copy `app-debug.apk` to the `static/` folder
3. Copy `app-debug.apk` to the `static/` folder
4. On your phone, go to your Cloudflare URL and click the APK file to download it

---

## STEP 3: Install the APK

1. On your phone, find the `app-debug.apk` file (probably in your **Downloads** folder)
2. Tap the file to open it
3. You'll see an "Install" screen with the app name and permissions
4. Tap **"Install"**
5. Wait for the installation to complete (a few seconds)
6. Tap **"Open"** to launch the app

**If you see a warning about "installing from unknown sources":**
- Tap **"Settings"** in the warning
- Turn on the toggle for **"Allow from this source"** (or "Unknown sources")
- Go back and tap **"Install"** again

---

## STEP 4: Test the App

### 4A — First launch

1. Open the **Runner Tracker** app on your phone
2. You'll see a dark screen with:
   - A text box that says **"Bib Number"**
   - A text box that says **"Server URL"**
   - A blue button that says **"START TRACKING"**

### 4B — Enter your details

1. In the **"Bib Number"** box, type your test bib number (e.g., `Test`)
2. In the **"Server URL"** box, type your public URL (e.g., `https://your-name.trycloudflare.com`)
3. Tap **"START TRACKING"**

### 4C — Allow permissions

1. Your phone will ask: **"Allow Runner Tracker to access location?"**
   - Tap **"Allow while using app"**
2. Your phone may ask: **"Allow Runner Tracker to run without battery optimization?"**
   - Tap **"Allow"** (this is important for background tracking)

### 4D — Verify tracking

1. You should see: **"📍 Tracking Test"** in green text
2. A notification will appear in your status bar: **"🏃 Tracking Race — Bib Test • GPS Active"**
3. After 10-20 seconds, check your laptop's map (the control room URL)
4. You should see a marker labeled **"Test"** on the map

### 4E — Test background tracking

1. Put your phone in your pocket
2. Wait 20 seconds
3. Check your laptop's map — the marker should still be there
4. The GPS is still sending pings even though the phone is in your pocket

**If you see the marker, it's working. The app is ready for runners.**

---

## STEP 5: Share the APK with Runners

### 5A — Create a sharing link

1. Upload `app-debug.apk` to a shareable location
2. Options:
   - Email it directly to runners
   - Drop it in the `static/` folder and share via your public URL
   - Share via WhatsApp, Telegram, or your group chat

### 5B — Share instructions with runners

Tell runners:
```
RACE TRACKING APP

1. Download and install the Runner Tracker app:
   [paste your APK download link here]

2. Open the app and enter:
   - Your bib number (e.g., 142)
   - Server URL: https://[your-tunnel-url].trycloudflare.com

3. Tap "START TRACKING"
4. Allow location permission
5. Allow battery optimization exemption
6. Put your phone in your pocket — tracking continues automatically

Your progress will appear live on the race map!
```

---

## RACE DAY CHECKLIST

- [ ] APK is uploaded to a shareable location (email, Cloudflare URL, etc.)
- [ ] Runners have downloaded and installed the app
- [ ] Runners have entered their bib number and server URL in the app
- [ ] Runners have allowed location permission
- [ ] Runners have allowed battery optimization exemption
- [ ] Server is running (`python3 server.py`)
- [ ] Tunnel is running (`cloudflared tunnel --url http://localhost:8090`)
- [ ] New tunnel URL shared with runners at least 1 hour before race start

---

## TROUBLESHOOTING

| Problem | What to do |
|---|---|
| **"App not installed" when trying to install APK** | Go to Settings → Security → Enable "Install unknown apps" for your browser/file manager |
| **GPS doesn't work after 5 minutes** | Go to Settings → Apps → Runner Tracker → Battery → Select "Unrestricted" |
| **App stops tracking after screen off** | Make sure you allowed battery optimization exemption in Step 4C |
| **Notification says "No location"** | Grant location permission and go outside for GPS fix |
| **App crashes on startup** | Check server URL format — must start with `https://` |
| **Runner can't find the APK after downloading** | Check Downloads folder, or check email attachment if sent via email |
| **Runner's phone says "This app is not compatible"** | The app requires Android 8.0 or higher — most phones from 2018+ are fine |

---

## IMPORTANT NOTES

- **The app requires Android 8.0 or higher** — phones from 2018+ should be fine
- **Location permission is required** — the app cannot track without it
- **Battery optimization must be disabled** — this is critical for background tracking
- **The server URL must be correct** — runners need the exact Cloudflare URL
- **The app is debug-only** — for production use, you'd sign it with a release key (not needed for race day)
- **The APK file is about 5MB** — easy to share via email or messaging apps

---

## Need Help?

If anything doesn't work as described, open an issue on GitHub or consult the troubleshooting section in [DEPLOY.md](../DEPLOY.md).
