# WifiAR

Android app that visualizes WiFi signal strength in AR.

**Parts complete:** 1–8 (scan · AR · fusion · heatmap · dead zones · speed test · backend sync · multi-network)

## Requirements

- Android Studio with AGP 8.13+ / JDK 17
- Physical ARCore-capable Android device (API 26+)
- Wi‑Fi + system location enabled
- Google Play Services for AR installed

## Setup

1. Open this folder in Android Studio.
2. Let Gradle sync (version catalog + wrapper).
3. Connect a physical device with USB debugging.
4. Run the `app` configuration.

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | Android requires location permission to access WiFi signal data (RSSI) |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | Read WiFi state and trigger scans |
| `NEARBY_WIFI_DEVICES` | Android 13+ WiFi access (location-related; `neverForLocation` **not** set) |
| `CAMERA` | ARCore camera passthrough |

## Module layout

```
com.wifiar.app/
  ├── scanner/           WifiScanner, RssiSample (Part 1)
  ├── ar/                ARSessionManager, Pose3D, HeatmapMeshBuilder
  ├── data/              SessionManager, DataFusionEngine
  │   ├── local/         Room entities, DAOs, WifiArDatabase
  │   ├── interpolation/ IdwInterpolator (Part 4)
  │   ├── analysis/      DeadZoneDetector (Part 5)
  │   └── speedtest/     Throughput + Ookla backends (Part 6)


  ├── ui/                Live Mapping, History, debug screens
  └── MainActivity.kt
```


## Live Mapping (Part 3)

1. Open the **Map** tab (location permission + camera).
2. Wait until AR tracking is healthy (“Slowly move your phone…” dismisses).
3. Tap **Start Session**, enter a label (e.g. `Home - Ground Floor`).
4. Walk the room — auto-scan runs when the WiFi throttle allows (~30 s).
5. View modes: **Raw points** / **Heatmap** / **Both**
6. After ~3+ samples (updates every 5 new samples), an IDW floor heatmap appears:
   - Smooth green → yellow → purple gradient by RSSI
   - Semi-transparent (~60% alpha) over the room
7. **End Session** persists samples to Room.
8. **History** tab lists past sessions with sample counts; tap for raw point list.

## Fusion notes

- Poses are buffered for ~2 s; each scan batch is tagged with nearest-neighbor pose by timestamp.
- Samples are only saved while AR tracking is `TRACKING`.
- Heatmap uses **2D IDW on (x, z)** only (single-floor); Y is used only for floor placement.
- Dead zones: contiguous cells with RSSI ≤ **−80 dBm** (`AppConfig.DEAD_ZONE_THRESHOLD_DBM`) — dark-red hatch + AR markers.
- Speed test: flip `AppConfig.SPEED_TEST_BACKEND` between `THROUGHPUT` (default, Cloudflare HTTP) and `OOKLA` (partner SDK + API key).



## Debug tabs

- **WiFi** — scanner-only debug list (Part 1)
- **AR** — pose HUD only (Part 2)
