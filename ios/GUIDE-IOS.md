# Runner Tracker — iOS App

> **Important:** This iOS app source code has **never been tested** and requires a Mac with Xcode to build. For most iPhone users, the **browser tracker** is the recommended option — it works immediately, no app install or developer account needed. See [DEPLOY.md](../DEPLOY.md) for instructions.

This guide exists for those who want to build and use the native iOS app.

---

## WHAT YOU ARE BUILDING

A phone app that runners install. They enter their bib number, tap START, and their GPS position appears on your live map — **even when the phone is in their pocket with the screen off**.

---

## STEP 1: Install Xcode

Xcode is Apple's free program for building iPhone apps.

### 1A — Download Xcode

1. Open the **App Store** on your Mac (the blue icon with an "A")
2. Search for **"Xcode"** in the search bar
3. Click **"Get"** next to Xcode (it's free)
4. Click **"Install"** or **"Open"** when prompted
5. Enter your Mac password if asked
6. Wait for the download and installation to complete

**This will take 30 minutes to 2 hours depending on your internet speed.** The download is about 12 GB.

**While you wait:** This guide will still be here. You can continue reading, but the next step (1B) requires Xcode to be fully installed first.

### 1B — First-time setup

1. After installation, Xcode will automatically open
2. If it doesn't open, find it in your **Applications** folder and double-click **Xcode**
3. When asked, accept the license agreement
4. Xcode will install additional components (this may take 10-15 minutes)
5. When you see the Xcode welcome screen with project options, you're ready

**Xcode is now installed.**

---

## STEP 2: Open the Runner Tracker Project

### 2A — Get the project

If you haven't already, clone or copy the `runner-tracker` folder to your Mac.

### 2B — Open the project in Xcode

1. Open Xcode (from Applications or the Dock)
2. If you see a welcome screen, click **"Open a project or file"**
3. If Xcode is already open, go to **File → Open** (or press `Cmd+O`)
4. Navigate to your `runner-tracker` folder
5. Click on the **`ios`** folder
6. Select the **`RunnerTracker.xcodeproj`** file (it has an Xcode icon)
7. Click **"Open"**

### 2C — Wait for Xcode to load

1. Xcode will show a progress bar at the bottom
2. It says **"Resolving packages"** or **"Downloading dependencies"**
3. **Wait for this to complete** — this may take 5-10 minutes the first time
4. When it's done, you'll see the project files in the left sidebar

**You should see a folder called `RunnerTracker` with these files inside:**
- `RunnerTrackerApp.swift`
- `ContentView.swift`
- `TrackingManager.swift`
- `Info.plist`
- `Assets.xcassets`

**If you see errors:** Don't worry — just wait a minute and try again. Xcode sometimes needs a second try.

---

## STEP 3: Connect Your iPhone

### 3A — USB connection (recommended)

1. Get your Lightning or USB-C cable (the one you use to charge your iPhone)
2. Plug one end into your Mac
3. Plug the other end into your iPhone
4. On your iPhone, if you see **"Trust This Computer?"**, tap **"Trust"**
5. Enter your iPhone passcode if asked

### 3B — Verify connection

1. In Xcode, look at the top toolbar (near the play button)
2. You should see your iPhone's name in a dropdown menu
3. If you see **"Select a destination"** or **"No devices found"**, check:
   - Is the cable connected properly?
   - Did you tap "Trust" on your iPhone?
   - Is your iPhone unlocked?

---

## STEP 4: Build the App

### 4A — Select your iPhone

1. In Xcode, look at the top toolbar (near the play button ▶)
2. Click the dropdown menu next to it
3. Select your iPhone from the list
4. If you don't see your iPhone, make sure it's connected and unlocked

### 4B — Build the app

1. In the top menu, click **Product**
2. Click **"Build"** (or press `Cmd+B`)
3. Xcode will show progress at the bottom: **"Building RunnerTracker"**
4. **Wait for this to complete** — this takes 2-5 minutes
5. When done, you'll see a green checkmark ✅ at the bottom

**If you see errors:** Open an issue on GitHub.

---

## STEP 5: Install and Test the App

### 5A — Run the app on your iPhone

1. In the top toolbar, click the **play button** ▶ (or press `Cmd+R`)
2. Xcode will install the app on your iPhone and launch it
3. Wait 10-20 seconds for the app to start on your iPhone

### 5B — First launch

1. The app will open on your iPhone
2. You'll see a dark screen with:
   - A title: **"🏃 Runner Tracker"**
   - A subtitle: **"Background GPS tracking"**
   - A text box that says **"Bib Number"**
   - A text box that says **"Server URL"**
   - A blue button that says **"START TRACKING"**

### 5C — Enter your details

1. In the **"Bib Number"** box, type your test bib number (e.g., `Test`)
2. In the **"Server URL"** box, type your public URL (e.g., `https://your-name.trycloudflare.com`)
3. Tap **"START TRACKING"**

### 5D — Allow permissions

1. Your iPhone will ask: **"Runner Tracker Would Like to Use Your Location"**
   - Tap **"Allow While Using App"**
2. The app will then ask: **"Allow 'Runner Tracker' to access your location even when you're not using the app?"**
   - Tap **"Allow"** (this is critical — it lets the app track when the phone is in your pocket)

### 5E — Verify tracking

1. You should see: **"📍 Tracking Test"** in green text
2. After 10-20 seconds, check your laptop's map (the control room URL)
3. You should see a marker labeled **"Test"** on the map

### 5F — Test background tracking

1. Press the side button on your iPhone to turn off the screen (put it in your pocket)
2. Wait 40-60 seconds
3. Turn your iPhone back on and open the app
4. Check your laptop's map — the marker should still be there
5. The GPS is still sending pings even with the screen off and phone in pocket (Core Location background mode, not a notification)

**If you see the marker, it's working. The app is ready for runners.**

---

## STEP 6: Share the App with Runners

You have two options for distributing the app to runners:

### Option A — TestFlight (recommended for groups)

TestFlight is Apple's free program for testing apps with a group.

1. In Xcode, go to **Product → Archive**
2. Wait for the archive to complete (2-5 minutes)
3. When done, the **Organizer** window will open automatically
4. Click **"Distribute App"**
5. Select **"TestFlight"** and click **"Next"**
6. Click **"Next"** through the export options
7. Click **"Upload"** and wait for it to complete (may take 10-20 minutes)
8. Go to [App Store Connect](https://appstoreconnect.apple.com)
9. Go to **TestFlight** → **iOS** → **"+"** (Add)
10. Create a new group called "Race Day Runners"
11. Add the runners' Apple IDs (their email addresses)
12. They'll receive an email with a link to install TestFlight and your app

**Note:** You need an Apple Developer account ($99/year) to use TestFlight. If you don't have one, use Option B.

### Option B — Direct installation (for trusted friends)

For a small group of trusted runners, you can install the app directly via USB.

1. Connect each runner's iPhone to your Mac
2. In Xcode, select their iPhone from the device dropdown
3. Click the play button ▶ to install the app
4. Tell them to open the app, enter their bib number + server URL, and tap START
5. They can then put their phone in their pocket — tracking works in the background

**Limitations:**
- You can only install on devices you've personally connected
- The app expires after 7 days unless you rebuild it
- Not suitable for large groups (50+ runners)

---

## STEP 7: Alternative — Browser Tracker for iPhone

If TestFlight setup is too complex for your first race, iPhone users can use the browser tracker:

1. Open Safari on their iPhone
2. Go to your Cloudflare URL (e.g., `https://water-honey-exact-certified.trycloudflare.com/tracker`)
3. Enter their bib number
4. Tap **"START TRACKING"**
5. Allow location permission when prompted

**Caveat:** The browser tracker requires the screen to stay on. It won't work if they put their phone in their pocket. For best results, recommend runners keep their phones in hand or a front pocket with the screen visible.

### QR Code Setup (for runners)

The map page has a QR code that runners can scan to automatically fill in the server URL and their bib number:

1. Runners tap the **📷** button in the app next to the Server URL field
2. They point their camera at the QR code on the map page
3. The app automatically fills in the server URL and bib number
4. They just tap **START TRACKING**

---

## RACE DAY CHECKLIST

- [ ] Xcode is installed and the app has been built
- [ ] App is installed on test iPhone (via TestFlight or USB)
- [ ] App has been tested (bib + URL → marker on map)
- [ ] Runners have installed the app (TestFlight or browser)
- [ ] Runners have entered their bib number and server URL
- [ ] Runners have allowed location permission
- [ ] Server is running (`python3 server.py`)
- [ ] Tunnel is running (`cloudflared tunnel --url http://localhost:8090`)
- [ ] New tunnel URL shared with runners at least 1 hour before race start

---

## TROUBLESHOOTING

| Problem | What to do |
|---|---|
| **Xcode says "Signing is required"** | Go to Xcode → Settings → Accounts → Add your Apple ID (free) |
| **"Unable to install app" on iPhone** | Make sure you tapped "Trust" when connecting, and the iPhone is unlocked |
| **GPS doesn't work after 5 minutes** | Go to Settings → Runner Tracker → Location → "Always" |
| **App stops tracking after screen off** | Make sure you allowed "Always" location permission in Step 5D |
| **Notification doesn't appear** | Go to Settings → Notifications → Runner Tracker → allow notifications |
| **App crashes on startup** | Check server URL format — must start with `https://` |
| **Runner can't find the app** | It should be on their home screen — search for "Runner Tracker" |
| **iPhone says "Untrusted Developer"** | Go to Settings → General → VPN & Device Management → Trust the developer |

---

## IMPORTANT NOTES

- **Xcode requires macOS 13 (Ventura) or higher** — check your Mac's version in About This Mac
- **Xcode requires about 30 GB of free disk space** — make sure you have room
- **Location permission is required** — the app cannot track without it
- **"Always" permission is critical** — this allows background tracking when the phone is in your pocket
- **The server URL must be correct** — runners need the exact Cloudflare URL
- **The app is debug-only** — for production use, you'd sign it with an Apple Developer account ($99/year)
- **TestFlight is free** — no cost to distribute to up to 10,000 testers

---

## APPLE DEVELOPER ACCOUNT ($99/year)

To distribute the app officially (App Store or TestFlight), you need an Apple Developer account:

1. Go to [developer.apple.com](https://developer.apple.com)
2. Click **"Enroll"** → **"Individual"**
3. Pay $99/year
4. Verify your identity (may take a few days)
5. Once enrolled, you can use TestFlight and sign apps for distribution

**For your first race:** You can use the browser tracker for iPhone users and save the $99 for future events.

---

## Need Help?

If anything doesn't work as described, open an issue on GitHub or consult the troubleshooting section in [DEPLOY.md](../DEPLOY.md).
