# WifiAR

### Visualize wireless coverage in augmented reality

WifiAR turns an Android phone into a **spatial Wi‑Fi survey tool**. Walk a room once and the app fuses live RSSI with ARCore pose tracking to paint a heatmap over the real floor, flag dead zones, compare nearby networks, measure throughput, and recommend where a router might perform better.

Built for people who care about coverage — homeowners, field techs, network students, and anyone tired of guessing why the bedroom has one bar.

---

## Features

| | |
|---|---|
| **Live AR heatmap** | Inverse-distance weighted signal map overlaid on the room |
| **Multi-network scan** | Captures *all* visible APs, not only the connected network |
| **Dead-zone detection** | Finds contiguous weak regions and marks them in AR |
| **Best network here** | Continuously estimates which SSID/BSSID is strongest at your pose |
| **Speed checkpoints** | Run download / upload / ping tests pinned to 3D locations |
| **Router placement** | Log-distance path-loss search for stronger candidate AP positions |
| **Session history** | Revisit past walkthroughs, compare networks, export results |
| **Share & export** | PNG heatmap with legend + CSV of raw samples via the system Share sheet |
| **Cloud sync** | Optional FastAPI + PostGIS backend with JWT auth and background upload |
| **Multi-day mapping** | Optional ARCore Cloud Anchors to keep the same physical origin across visits |
| **Tunable model** | RSSI color thresholds, grid resolution, indoor/open path-loss presets |

---

## Screenshots

> *Add device captures here for your repo / report:*  
> `docs/screenshots/map.png` · `docs/screenshots/heatmap.png` · `docs/screenshots/history.png`

---

## How it works

```
┌─────────────┐     ┌──────────────┐
│  Wi‑Fi RSSI │     │ ARCore pose  │
│  (all APs)  │     │  (metres)    │
└──────┬──────┘     └──────┬───────┘
       │                   │
       └─────────┬─────────┘
                 ▼
        ┌────────────────┐
        │  Data fusion   │  tag each scan with nearest pose
        │  + Room store  │
        └────────┬───────┘
                 ▼
        ┌────────────────┐
        │  IDW heatmap   │  floor-plane interpolation
        │  + analysis    │  dead zones · compare · path loss
        └────────┬───────┘
                 ▼
   AR overlay · export · optional cloud sync
```

1. **Scan** — Android `WifiManager` results for every BSSID, with throttle-aware scheduling.  
2. **Track** — ARCore (via SceneView) provides a local metric coordinate frame.  
3. **Fuse** — Each scan batch is matched to the nearest recent pose and persisted.  
4. **Interpolate** — IDW builds a floor-plane grid; a recompute gate keeps the UI responsive.  
5. **Analyze** — Dead-zone flood fill, per-network stats, path-loss router search.  
6. **Render** — Compact AR spheres and a textured heatmap plane; draw count is capped for performance.  
7. **Share / sync** — Export PNG/CSV locally, or push sessions to the optional backend.

---

## Tech stack

**Android app**

- Kotlin · Jetpack Compose · Material 3  
- ARCore · SceneView (Filament)  
- Room · Coroutines · WorkManager  
- Retrofit · OkHttp · Moshi  
- EncryptedSharedPreferences (auth tokens)

**Backend** (`wifiar-backend/`)

- FastAPI · PostgreSQL / PostGIS · Alembic · JWT  

---

## Repository layout

```
WifiAR/
├── app/                    Android application
│   └── src/main/java/com/wifiar/app/
│       ├── scanner/        Wi‑Fi acquisition
│       ├── ar/             Pose, heatmap mesh, Cloud Anchors
│       ├── data/           Fusion, Room, analysis, export, sync
│       └── ui/             Screens, theme, shared components
└── wifiar-backend/         Optional API + spatial database
```

---

## Getting started

### Requirements

- Android Studio (JDK 17)  
- Physical device with **ARCore** support, API **26+**  
- USB debugging enabled  

> Emulators are a poor fit: reliable Wi‑Fi RSSI and ARCore tracking need real hardware.

### Run the app

```bash
git clone https://github.com/Kadiwalhussain/WifiAR.git
cd WifiAR
```

1. Open the project in Android Studio and wait for Gradle sync.  
2. Connect a physical device.  
3. Run the **app** configuration.  
4. Complete onboarding and grant **location**, **camera**, and (Android 13+) **nearby Wi‑Fi**.  
5. Open **Map → Start Session**, walk slowly, and cover corners of the space.

### Optional: backend

```bash
cd wifiar-backend
docker compose up -d
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Health: `http://localhost:8000/health`  
- Docs: `http://localhost:8000/docs`  

Point the app at your machine by setting `AppConfig.API_BASE_URL`:

| Target | Example |
|--------|---------|
| Emulator → host | `http://10.0.2.2:8000/` |
| Phone on LAN | `http://192.168.x.x:8000/` |

Register or sign in under **Account** to enable background session sync.

### Optional: ARCore Cloud Anchors

For multi-visit origin alignment in the same physical room:

1. Create a Google Cloud project and enable the **ARCore API**.  
2. Create an API key (restrict to your package / SHA-1 if you prefer).  
3. Add to `local.properties` (never commit secrets):

```properties
arcore.api.key=YOUR_KEY
```

4. Rebuild. Ending a session may host an anchor; starting again with the same location name offers **Resume previous**.

Without a key, mapping and local resume still work — only cloud origin re-alignment is unavailable.

Hosting Cloud Anchors uploads feature points to Google; see [ARCore data privacy](https://developers.google.com/ar/data-privacy).

---

## Using the app

1. **Map** — start a named session, walk the space, switch Points / Heatmap / Both.  
2. **Speed test** — pin throughput at the current pose.  
3. **History** — open a session for dead zones, network comparison, router suggestion, export.  
4. **Settings** — adjust RSSI color cutoffs, grid cell size, and path-loss preset.  
5. **Account** — sign in to sync pending sessions when online.

**Tips for a good map**

- Move slowly and pause at corners so scans can complete.  
- Cover the full footprint; sparse walks produce patchy heatmaps.  
- Prefer well-lit, textured surfaces for stable AR tracking.  

---

## Design & performance

WifiAR is tuned for **smooth AR** and a compact HUD:

- Spatial downsampling of sample spheres (budgeted draw count)  
- Small markers so the room stays readable  
- Heatmap recompute only after meaningful new sample volume  
- Heavy analysis off the main thread  
- Time-bounded Cloud Anchor host / resolve so session end never stalls  

---

## Limitations

Be explicit about what the product does *not* claim:

| Topic | Reality |
|-------|---------|
| Platform | Android only — iOS does not expose equivalent free-form Wi‑Fi RSSI APIs |
| Scan rate | Android throttles scans; dense maps need a deliberate walk |
| Path-loss model | Single exponent, no wall or material sensing — placement is a **heuristic** |
| Coordinates | AR-local frame, not GPS or CAD floor plans |
| Cloud Anchors | Requires a Google API key and network connectivity |

---

## Configuration

Key knobs live in `AppConfig` and **Settings**:

- Dead-zone threshold (default ≤ −80 dBm)  
- Coverage / color tiers for heatmaps  
- Grid cell size for IDW  
- Path-loss exponent presets (indoor vs open)  
- API base URL and speed-test backend selection  

---

## Contributing

Issues and pull requests are welcome. Prefer small, focused changes with a clear description of behavior and device impact (ARCore behavior varies by hardware).

---

## License

This project is provided for learning, research, and practical use. Adapt it to your needs; if you ship Cloud Anchors, follow Google’s ARCore policies and privacy requirements.
