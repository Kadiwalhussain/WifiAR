# WifiAR

Android app that visualizes **Wi‑Fi signal strength in AR**, detects dead zones, compares networks, runs speed tests, suggests router placement, and optionally syncs sessions to a FastAPI + PostGIS backend.

**Status:** Parts 1–10 complete (student project end-to-end).

---

## Architecture overview

```
┌─────────────────────────────────────────────────────────────┐
│ Android (Kotlin + Jetpack Compose + ARCore / SceneView)     │
│  scanner/     WifiScanner — all visible APs + RSSI           │
│  ar/          Pose, Cloud Anchors, heatmap mesh              │
│  data/        Fusion, IDW, dead zones, comparison, path-loss │
│               Room DB, export, sync (Retrofit + WorkManager) │
│  ui/          Map · History · Account · Settings · Onboarding│
└────────────────────────────┬────────────────────────────────┘
                             │ JWT bulk upload
                             ▼
┌─────────────────────────────────────────────────────────────┐
│ wifiar-backend (FastAPI + PostgreSQL/PostGIS)                 │
│  Auth · sessions · bulk RSSI/speed points · GP heatmap       │
└─────────────────────────────────────────────────────────────┘
```

| Area | Implementation |
|------|----------------|
| Wi‑Fi scans | `WifiManager` + throttle-aware Flow (all BSSIDs) |
| Pose | ARCore via SceneView `ARSceneView` |
| Heatmap | Client IDW; server Gaussian Process |
| Dead zones | Flood-fill on IDW grid ≤ threshold |
| Speed test | HTTP throughput (Cloudflare) or Ookla stub |
| Multi-network | Per-BSSID IDW + Best Network Here |
| Router tip | Log-distance path loss grid search |
| Sync | Retrofit + WorkManager when session ends |
| Continuity | ARCore Cloud Anchors (optional API key) |

---

## Known limitations (be honest in demos)

1. **iOS / non-Android** — apps cannot freely read Wi‑Fi RSSI the same way; this project is Android-only by design.
2. **Scan throttling** — Android 9+ limits scan rate (~4 / 2 min). Client enforces a 30s cooldown.
3. **Path-loss model** — single exponent `n`, **no wall/material sensing**. Placement tips are heuristics, not RF simulation.
4. **Cloud Anchors** — require Google Cloud ARCore API key; without it, resume still reopens session data but may not re-align AR origin.
5. **Emulators** — Wi‑Fi scan + ARCore are unreliable; use a **physical ARCore device**.
6. **Coordinate frames** — poses are local to the AR session origin (not GPS / floor plans).

---

## Prerequisites

- Android Studio (JDK 17), physical device API 26+ with ARCore
- Optional backend: Docker, Python 3.10+

---

## Android setup

1. Open this repo in Android Studio; Gradle sync.
2. Connect a physical device with USB debugging.
3. **Optional Cloud Anchors** (multi-day AR origin alignment — see below).
4. **Optional backend URL:** set `AppConfig.API_BASE_URL` in
   `app/src/main/java/com/wifiar/app/AppConfig.kt`  
   - Emulator → host: `http://10.0.2.2:8000/`  
   - Device on LAN: `http://<your-pc-ip>:8000/`
5. Run the `app` configuration.

First launch shows a short **onboarding** tutorial, then Map / History / Account / Settings.

### Cloud Anchors setup (optional, Google Cloud)

Without an API key the app still maps and can **reopen prior sessions** to append
samples. Cloud Anchors only re-align the AR **coordinate origin** across days.

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and create/select a project.
2. Enable **ARCore API** (APIs & Services → Library → “ARCore API”).
3. Create an **API key** (Credentials → Create credentials → API key).
   - Restrict the key to the ARCore API.
   - Optionally restrict by Android app package `com.wifiar.app` + your debug/release SHA-1.
4. Put the key in **either**:
   - `local.properties` (preferred, not committed):
     ```properties
     arcore.api.key=YOUR_ACTUAL_KEY
     ```
   - or replace the placeholder in `AndroidManifest.xml` meta-data
     `com.google.android.ar.API_KEY`.
5. Rebuild the app. On **End Session**, if tracking is good, WifiAR hosts a Cloud
   Anchor (TTL ~30 days) and stores the id on the session.
6. Next time you start mapping with the **same location name**, choose
   **Resume previous** — the app resolves the anchor (or falls back to a local
   origin if resolve fails).

**Privacy:** hosting uploads feature points to Google. Disclose this in demos;
see [ARCore data privacy](https://developers.google.com/ar/data-privacy).

---

## Backend setup

```bash
cd wifiar-backend
docker compose up -d          # PostGIS on :5432
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env          # defaults match docker-compose
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Health: http://localhost:8000/health  
- Swagger: http://localhost:8000/docs  

Endpoints: `POST /auth/register`, `POST /auth/login`, session CRUD + bulk points, `GET /sessions/{id}/heatmap` (GP).

---

## Demo script (suggested live order)

1. **Onboarding** — permissions honesty (location for Wi‑Fi RSSI).  
2. **Map → Start Session** — walk slowly, cover corners; watch sample count + Best Network Here.  
3. **Heatmap / Both** — green / yellow / purple / dark-red dead zones.  
4. **Run Speed Test Here** — cyan checkpoint marker.  
5. **End Session** — optional Cloud Anchor host + WorkManager sync if logged in.  
6. **History** — open session → dead zones → **Compare networks** → per-SSID AR heatmap.  
7. **Suggest router placement** — top 3 + current vs recommended; **Show #1 in AR**.  
8. **Export & share** — PNG heatmap + CSV via ShareSheet.  
9. **Account** — register/login, sync pending.  
10. **Settings** — tweak RSSI thresholds / grid / path-loss preset.

---

## Project layout

```
WifiAR/
  app/src/main/java/com/wifiar/app/
    scanner/          Part 1
    ar/               Parts 2, 4, 10 (Cloud Anchors)
    data/             Fusion, Room, analysis, export, sync, speedtest
    ui/               Screens + onboarding + settings
  wifiar-backend/     Part 7 FastAPI + PostGIS
  README.md
```

---

## Report / screenshots checklist

Capture (device): permission rationale · live heatmap · dead zone card · speed test · network comparison table · router #1 AR marker · export share sheet · settings · history empty state.

---

## License / course use

Student project code — adapt freely for academic submission; document Google ARCore / Cloud data policies if using Cloud Anchors in demos.
