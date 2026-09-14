# BLE Proximity Tracking

Every runner's Android phone is BOTH a GPS beacon AND a detector for other runners' BLE tags. No checkpoints, no volunteers, no fixed infrastructure.

## The Core Idea

Since 68% of phones are Android, runners constantly pass within ~10m of Android phones on the course. Each runner wears a small BLE tag. When runner A's phone detects runner B's tag, the app sends a proximity event to the server:

```
"Bib 142 saw Bib 008 at (35.1, -111.7) at 14:32:05"
```

This gives you:
- **Real-time GPS** from every runner's phone
- **Relative position data** — who was near whom, when and where
- **Checkpoint timing** — when a runner passes a known location (via another runner's detection)
- **No checkpoint infrastructure needed**

## How It Works

```
Runner A (Bib 142)          Runner B (Bib 008)
    📱 Android phone            📱 Android phone
    📡 BLE tag (own)            📡 BLE tag (own)
         │                          │
         │  ──── pass within 10m ────│
         │                          │
         ▼                          ▼
    Phone A scans              Phone B scans
    detects tag B              detects tag A
         │                          │
         ▼                          ▼
    POST /api/proximity       POST /api/proximity
    {seen_by: 142,            {seen_by: 008,
     seen: 008,                seen: 142,
     lat, lng, ts}             lat, lng, ts}
         │                          │
         ▼                          ▼
    ┌─────────────────────────────────┐
    │         Server (you)            │
    │  • GPS positions (all runners)  │
    │  • Proximity events (who saw    │
    │    whom, where, when)           │
    └─────────────────────────────────┘
```

## Hardware

**BLE Beacon Tags** (one per runner):
- iBeacon or Eddystone tags
- Battery-powered (CR2032, lasts 1-3 years)
- ~$3-5 each
- Attach to bib, hat, or clothing
- Broadcast interval: 1-5 seconds (default is fine)

**Android Phones:**
- Each runner's phone (already needed for GPS tracking)
- No additional phones needed at checkpoints

## Setup

### 1. Program Your Tags

Each BLE tag needs a unique identity:
- **iBeacon**: UUID + major + minor (set via manufacturer's app or nRF Connect)
- **Eddystone**: namespace + instance
- **Fallback**: MAC address (works without special programming)

### 2. Create Your Tag Map

Map each tag's identity to a bib number:

```json
{
  "IB:00112233-4455-6677-8899-aabbccddeeff:1:1": "Bib 001",
  "IB:00112233-4455-6677-8899-aabbccddeeff:1:2": "Bib 002",
  "IB:00112233-4455-6677-8899-aabbccddeeff:1:3": "Bib 142"
}
```

**Tag identity formats:**
- iBeacon: `IB:<uuid>:<major>:<minor>`
- Eddystone: `ED:<namespace>:<instance>`
- MAC fallback: `MAC:<AA:BB:CC:DD:EE:FF>`

### 3. Configure Each Runner's Phone

1. Open the Runner Tracker app
2. Enter their **bib number**
3. Enter the **server URL** (Cloudflare tunnel)
4. Paste the **full tag map JSON** (all runners)
5. Tap **▶ TRACK GPS**
6. Grant Bluetooth permission when prompted

### 4. Race Day

- Each runner puts their phone in a pocket/armband
- App runs GPS tracking + BLE scanning in the background
- When runners pass each other, proximity events are logged
- You watch the live map with all runners' positions

## Server Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/api/ping` | POST | GPS position update (every 20s) |
| `/api/proximity` | POST | BLE tag detection event |
| `/api/proximity` | GET | All proximity events |
| `/api/runners` | GET | Full snapshot (runners + checkpoints + proximity) |

**Proximity event format:**
```json
{
  "seen_by": "Bib 142",
  "seen": "Bib 008",
  "lat": 35.1,
  "lng": -111.7,
  "rssi": -55,
  "ts": 1756000000.0
}
```

## Detection Range

- **iBeacon/Eddystone**: 10-30m (depending on TX power)
- **MAC fallback**: ~10m
- **De-dupe**: 30-second cooldown per tag (prevents spam)

## Limitations

- **Android only for detection**: Only Android phones can scan BLE tags (iOS can't do background BLE scanning)
- **Battery**: Tags use coin cell batteries (1-3 years)
- **Range**: 10-30m — runners must pass relatively close
- **Tag IDs**: You need to know each tag's identity before the race
- **Not all runners may have Android**: iOS runners still get GPS tracking, but can't detect others' tags

## Advantages

| | Fixed Checkpoints | BLE Proximity |
|---|---|---|
| Infrastructure | Computer + reader per checkpoint | None |
| Volunteers | Needed at each checkpoint | None |
| Works in wilderness | No (needs power + computer) | Yes (phone + cellular) |
| Granularity | Only at checkpoints | Anywhere runners pass |
| Cost | ~$100+ per checkpoint | ~$4 per runner (tag) |

## Troubleshooting

**"Bluetooth OFF — BLE detection disabled"**
- Turn on Bluetooth. GPS tracking still works without BLE.

**No proximity events**
- Tags may not be powered on
- Runners may not be passing within 10-30m
- Check tag map matches actual tag IDs (use nRF Connect to verify)

**Too many proximity events**
- Reduce broadcast interval on tags (e.g., 5s instead of 1s)
- Increase de-dupe window in code (currently 30s)
