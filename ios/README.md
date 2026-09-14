# Runner Tracker — iOS App
This app is untested.
 If you want real iPhone support, the options are: are:                                                           
 Test and verify the native iOS app (needs a Mac + account)                                                                               
 - Verify Overlander actually works (and document exact setup steps)                                                                                       
 - Accept that iPhone users won't get background tracking unless they leave their screen on. (Battery Drain) 
 a 
This is a lightweight iOS app for background GPS tracking. GPS continues working even when the phone is in a pocket and the screen is off. But it will require a MAC Developer account. (Apple is so mean!)

## Features

- **Background tracking** — GPS continues when phone is in pocket, screen off
- **Always location permission** — iOS Core Location keeps tracking alive
- **Simple UI** — Enter bib number, server URL, tap START
- **QR code scanner** — Scan map QR code to auto-fill URL and bib
- **Deep link support** — `runnertracker://` URL scheme for auto-setup
- **Battery efficient** — 20-second ping interval
- **SwiftUI** — Modern Apple UI framework

## Building the iOS App

Requires a Mac with Xcode installed. Follow the complete guide in `GUIDE-IOS.md` to:
1. Install Xcode (free from the App Store)
2. Open the Runner Tracker project
3. Build and install the app on an iPhone
4. Test it works (bib + URL → marker on map)
5. Share with runners via TestFlight or direct install

## Distribution Options

| Method | Best For | Background? |
|---|---|---|
| **iOS Shortcuts** | Most iPhone users | ⚠️ Limited background |
| **Browser tracker** | Quick setup, no install | ❌ Screen must stay on |
| **TestFlight** | Groups of 50+ runners | ✅ Yes (requires developer account) |
| **Direct install** | Small trusted groups | ✅ Yes (debug builds, 7-day expiry) |

**For first race:** Most iPhone users should use the **browser tracker** (no setup needed) or **iOS Shortcuts** (built-in automation, limited background tracking). See [DEPLOY.md](../DEPLOY.md) for full instructions.

**Note:** No third-party App Store app will work — they can't POST to your custom server. The only options are the included iOS app, Shortcuts, or the browser tracker.

## Configuration

- **Ping interval**: 20 seconds (set in `TrackingManager.swift`)
- **Server API**: Posts to `/api/ping` — same server as Android and browser
- **Location**: Requires "Always" permission for background tracking

## Same Server

This app uses the EXACT same server API as the Android app and the browser tracker:
- `POST /api/ping` — GPS ping
- `GET /api/runners` — live runner list
- `GET /api/checkpoints` — checkpoint events

No server changes needed — just the same Cloudflare URL.
