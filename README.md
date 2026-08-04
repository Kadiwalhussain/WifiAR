# WifiAR

**See Wi‑Fi the way it feels in the room.**  
WifiAR is an Android AR app that paints live signal heatmaps over real space, finds dead zones, compares networks, runs speed tests, suggests better router placement, and exports shareable maps.

> Built as a full end-to-end student project (Parts 1–10) — polished for demos, fast on device, honest about RF limits.

---

## Why it feels futuristic

| Experience | What you get |
|------------|----------------|
| **AR HUD** | Compact glass panels, neon signal palette, pulse tracking status |
| **Live spheres** | Tiny colored RSSI balls in 3D (spatially downsampled so AR stays smooth) |
| **Heatmap plane** | IDW floor texture — green / amber / magenta / dead-zone red |
| **Motion UI** | Tab transitions, segmented view mode, spring buttons, onboarding pager |
| **Stability** | Session start/end hardened, Cloud Anchor timeouts, recycled-bitmap guards |

---

## How the app works (pipeline)

```
Camera + ARCore pose ──┐
                       ├──► DataFusionEngine ──► Room DB (samples / sessions / speeds)
Wi‑Fi scan (all APs) ──┘              │
                                      ▼
                         IDW grid → Heatmap mesh → Dead zones
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
            Network compare    Path-loss router    PNG/CSV export
                    │           placement            ShareSheet
                    ▼                 ▼
              Best network here   Golden AR marker
                    │
                    └── optional ──► FastAPI + PostGIS (JWT sync)
```

### Layer by layer

1. **Scan** — `WifiScanner` reads *every* nearby AP (not only the connected one). Android scan throttle is respected (~30s cooldown).
2. **Pose** — ARCore via SceneView tracks device position in local metric space.
3. **Fusion** — Each scan batch is tagged with the nearest recent pose and stored in Room.
4. **Heatmap** — Inverse Distance Weighting (IDW) on the floor plane `(x, z)`; recompute is gated so UI never blocks on huge grids.
5. **Dead zones** — Flood-fill cells ≤ threshold (default −80 dBm).
6. **Speed tests** — HTTP throughput (Cloudflare-style) or Ookla stub; cyan AR checkpoints.
7. **Multi-network** — Per-BSSID stats + “Best network here” while you walk.
8. **Router placement** — Log-distance path loss grid search (heuristic; **no wall detection**).
9. **Cloud** — Optional FastAPI + PostGIS + WorkManager sync.
10. **Continuity** — Optional ARCore Cloud Anchors to re-align origin across days; resume still works locally without an API key.

---

## Architecture (code map)

```
app/src/main/java/com/wifiar/app/
├── scanner/          Wi‑Fi RSSI acquisition
├── ar/               Pose, HeatmapMeshBuilder, CloudAnchorManager
├── data/
│   ├── fusion + SessionManager (crash-safe lifecycle)
│   ├── local/        Room entities & DAOs
│   ├── interpolation/ IDW + recompute gate
│   ├── analysis/     Dead zones, compare, path-loss, router
│   ├── speedtest/    Throughput / Ookla
│   ├── sync/         Retrofit + WorkManager + JWT
│   ├── export/       PNG heatmap + CSV + ShareSheet
│   └── UserPreferences  Settings (thresholds, grid, path-loss)
├── ui/
│   ├── components/   Glass HUD, pills, segmented control, empty states
│   ├── theme/        Compact typography + neon dark/light schemes
│   └── screens       Map · History · Account · Settings · Onboarding
└── AppConfig         Tunable radii, timeouts, API base URL
```

Backend: `wifiar-backend/` — FastAPI, PostgreSQL/PostGIS, Alembic, JWT auth.

---

## Performance design (why it stays fast)

- **AR sphere budget** — multi-AP walks can produce *thousands* of sample rows. Rendering is **spatially downsampled** (max ~140 spheres) with strongest RSSI per cell.
- **Smaller markers** — sample / dead-zone / speed / router balls are intentionally small so the room stays readable.
- **Heatmap recompute gate** — IDW only re-runs after enough new samples.
- **Off-main analysis** — heavy work on `Dispatchers.Default`.
- **Cloud Anchor timeouts** — host/resolve abort after ~12s so End Session never hangs.
- **SessionManager** — never stacks two active sessions; DB failures clear UI state instead of crashing.

---

## Quick start (Android)

**Requirements:** Android Studio (JDK 17), physical ARCore device, API 26+.

1. Clone & open the repo; Gradle sync.
2. Connect a USB-debuggable phone with ARCore.
3. Run the `app` configuration.
4. First launch → short onboarding → grant **location + camera** (+ nearby Wi‑Fi on Android 13+).
5. **Map → Start Session** → walk slowly, cover corners → watch balls + heatmap.

### Optional: Cloud Anchors (multi-day origin)

1. Google Cloud → enable **ARCore API** → create API key.
2. In `local.properties` (not committed):
   ```properties
   arcore.api.key=YOUR_KEY
   ```
3. Rebuild. On **End Session**, WifiAR may host an anchor; next visit with the same location name → **Resume previous**.

Without a key: sessions still resume *data*; AR origin may not re-align (documented fallback).

### Optional: backend sync

```bash
cd wifiar-backend
docker compose up -d
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# configure DATABASE_URL / JWT if needed
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Set `AppConfig.API_BASE_URL` to your PC’s LAN IP for a real device (emulator: `http://10.0.2.2:8000/`).

---

## Demo script (live)

1. **Onboarding** — explain location permission honesty.  
2. **Start Session** — name the room; walk edges + corners.  
3. **View modes** — Points / Heatmap / Both (segmented control).  
4. **Dead zone chips** — tap red AR markers.  
5. **Speed test** — cyan ball + Mbps label.  
6. **End Session** — optional Cloud host + background sync.  
7. **History** — export PNG + CSV via ShareSheet.  
8. **Compare networks** → **Router placement** → golden AR marker.  
9. **Settings** — tweak RSSI cutoffs / grid / path-loss preset.  

---

## Known limitations (state these in viva)

1. **Android-only** — iOS does not expose free-form Wi‑Fi RSSI the same way.  
2. **Scan throttling** — platform limits scan rate; denser maps need patience.  
3. **Path-loss is simplified** — single exponent `n`, **no walls/materials**.  
4. **Cloud Anchors** need a Google API key + feature-point upload disclosure.  
5. **Emulators** are unreliable for Wi‑Fi + ARCore — use a real phone.  
6. **Coordinates** are AR-local, not GPS floor plans.

---

## UI & product polish (this release)

- Compact Material 3 **typography scale** (less “everything is huge”)
- Neon **dark cyber** palette with glass HUD over AR
- Animated **bottom nav**, onboarding pager, calibration card
- Empty states for history; denser session cards
- Safe session lifecycle + AR render caps to prevent freezes/crashes

---

## Project status

| Part | Feature |
|------|---------|
| 1 | Wi‑Fi scanning |
| 2 | ARCore pose |
| 3 | Fusion + Room samples |
| 4 | IDW heatmap |
| 5 | Dead zones |
| 6 | Speed tests |
| 7 | Backend + sync |
| 8 | Multi-network compare |
| 9 | Router placement |
| 10 | Export, Cloud Anchors, onboarding, settings, polish |

Remaining: report writing, viva prep, professor feedback.

---

## License / course use

Student project code — adapt freely for academic submission. If you demo Cloud Anchors, disclose Google ARCore data practices.
