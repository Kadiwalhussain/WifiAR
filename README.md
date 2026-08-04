<p align="center">
  <img src="docs/assets/logo.svg" alt="WifiAR" width="320"/>
</p>

<p align="center">
  <strong>Visualize wireless coverage in augmented reality</strong>
</p>

<p align="center">
  <img src="docs/assets/icon.svg" alt="WifiAR icon" width="72"/>
</p>

<p align="center">
  <a href="docs/SETUP.md"><img alt="Setup" src="https://img.shields.io/badge/docs-setup-00E5FF?style=flat-square"/></a>
  <a href="docs/ARCHITECTURE.md"><img alt="Architecture" src="https://img.shields.io/badge/docs-architecture-69F0AE?style=flat-square"/></a>
  <a href="docs/API.md"><img alt="API" src="https://img.shields.io/badge/docs-API-E040FB?style=flat-square"/></a>
  <a href="docs/USER_GUIDE.md"><img alt="User guide" src="https://img.shields.io/badge/docs-user%20guide-90CAF9?style=flat-square"/></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white"/>
  <img alt="ARCore" src="https://img.shields.io/badge/AR-ARCore-4285F4?style=flat-square&logo=google&logoColor=white"/>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white"/>
</p>

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

## Documentation

| Guide | Description |
|-------|-------------|
| [Setup](docs/SETUP.md) | Clone, run, backend, secrets |
| [User guide](docs/USER_GUIDE.md) | How to map rooms and read the UI |
| [Architecture](docs/ARCHITECTURE.md) | Packages, data flow, performance |
| [API](docs/API.md) | Backend endpoints |
| [Icons](docs/ICONS.md) | Launcher, nav, and brand assets |
| [Contributing](docs/CONTRIBUTING.md) | PR workflow and checklist |
| [Changelog](docs/CHANGELOG.md) | Release history |
| [Screenshots](docs/screenshots/README.md) | Where to place demo captures |

---

## Screenshots

> Add device captures under [`docs/screenshots/`](docs/screenshots/README.md), then link them here.

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

**Backend** (`wifiar-backend/`)

- FastAPI · PostgreSQL / PostGIS · Alembic · JWT  

---

## Quick start

```bash
git clone https://github.com/Kadiwalhussain/WifiAR.git
cd WifiAR
```

1. Open in Android Studio (JDK 17) and sync Gradle.  
2. Connect a physical **ARCore** device (API 26+).  
3. Run the **app** configuration.  
4. Grant location + camera (+ nearby Wi‑Fi on Android 13+).  
5. **Map → Start Session** → walk slowly and cover corners.

Full instructions: **[docs/SETUP.md](docs/SETUP.md)**

### Optional backend

```bash
cd wifiar-backend
docker compose up -d
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Set `AppConfig.API_BASE_URL` to your LAN IP (or `http://10.0.2.2:8000/` for the emulator).

### Optional Cloud Anchors

```properties
# local.properties
arcore.api.key=YOUR_KEY
```

Enable the **ARCore API** in Google Cloud. See [SETUP.md](docs/SETUP.md).

---

## Repository layout

```
WifiAR/
├── app/                 Android client
├── wifiar-backend/      Optional API + PostGIS
├── docs/                Guides, icons notes, assets
│   ├── assets/          logo.svg · icon.svg
│   └── screenshots/     place demo images here
└── README.md
```

---

## Limitations

| Topic | Reality |
|-------|---------|
| Platform | Android only — iOS does not expose equivalent free-form Wi‑Fi RSSI APIs |
| Scan rate | Android throttles scans; dense maps need a deliberate walk |
| Path-loss model | Single exponent, no wall sensing — placement is a **heuristic** |
| Coordinates | AR-local frame, not GPS or CAD floor plans |
| Cloud Anchors | Requires a Google API key and network connectivity |

---

## Contributing

See [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md). Issues and focused pull requests are welcome.

---

## License

This project is provided for learning, research, and practical use. Adapt it to your needs; if you ship Cloud Anchors, follow Google’s ARCore policies and privacy requirements.
