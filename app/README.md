# Runner Tracker — Android App

A lightweight Android app for background GPS tracking. Runs as a foreground service with a persistent notification, so GPS continues working even when the phone is in your pocket and the screen is off.

## Pre-built APK

A ready-to-install APK is included:
```
app/build/outputs/apk/debug/app-debug.apk
```
(5.5MB)

To rebuild from source, see below.

## Features

- **Background tracking** — GPS continues running when phone is in pocket
- **Persistent notification** — Android keeps the service alive with a notification bar icon
- **Simple UI** — Enter bib number, server URL, tap START
- **Battery efficient** — 20-second ping interval, no Wake Lock needed
- **Zero dependencies** — Uses only Android SDK
- **BLE proximity scanning** — Detects other runners' BLE beacon tags

## Building from source

### Prerequisites
- Java 8+ (JDK 21 works)
- Android SDK command-line tools
- Gradle 8.5+

### Build command

```bash
export ANDROID_HOME=~/android-sdk
export GRADLE_HOME=~/gradle/gradle-8.5
export PATH=$PATH:$GRADLE_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

cd runner-tracker
gradle assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

## Installation

1. Copy `app-debug.apk` to your phone (USB, email, Cloudflare URL)
2. Tap the APK to install
3. Allow "Install from unknown sources" if prompted
4. Open the app — enter bib number + server URL, tap START

## Configuration

- **Ping interval**: 20 seconds (set in `TrackingService.kt`)
- **Server API**: Posts to `/api/ping` — same server as browser tracker

## Distribution

For your running club:
1. Email the APK directly to runners
2. Upload to Cloudflare URL and share link
3. Use USB to transfer to each phone

For production (Google Play Store), you'll need to:
1. Sign the APK with a release key
2. Create a Google Play Developer account ($25 one-time fee)
3. Upload the signed APK
