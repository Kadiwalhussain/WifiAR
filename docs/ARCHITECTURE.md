# Architecture

WifiAR is split into a mobile client and an optional spatial backend.

## System overview

```
┌──────────────────────────────────────────────────────────────┐
│                     Android client                           │
│  UI (Compose)  →  Domain services  →  Room / AR / Network    │
└────────────────────────────┬─────────────────────────────────┘
                             │ HTTPS + JWT (optional)
                             ▼
┌──────────────────────────────────────────────────────────────┐
│              wifiar-backend (FastAPI + PostGIS)               │
│  Auth · Sessions · Bulk points · Heatmap queries             │
└──────────────────────────────────────────────────────────────┘
```

## Client packages

| Package | Responsibility |
|---------|----------------|
| `scanner/` | Wi‑Fi scan lifecycle, throttle handling, AP list |
| `ar/` | Pose tracking, heatmap mesh, Cloud Anchors |
| `data/` | Fusion, persistence, analysis, export, sync |
| `ui/` | Screens, theme, shared components |
| `AppConfig` | Global thresholds, URLs, marker sizes |

## Data flow

1. **Acquire** — `WifiScanner` emits BSSID/RSSI batches; `ARSessionManager` emits pose.
2. **Fuse** — `DataFusionEngine` stamps each AP sample with the nearest pose and writes Room.
3. **Analyze** — IDW interpolation, dead-zone flood fill, network comparison, path-loss placement.
4. **Present** — SceneView renders heatmap plane + spheres; Compose HUD shows status.
5. **Export / sync** — PNG/CSV via FileProvider; WorkManager uploads when authenticated.

## Persistence (Room)

- `mapping_sessions` — session metadata, sync flags, optional Cloud Anchor id  
- `rssi_samples` — fused points (pose + SSID/BSSID/RSSI)  
- `speed_tests` — throughput checkpoints  

## Performance notes

- Heatmap recompute is gated by sample delta.  
- AR sphere count is spatially capped.  
- Analysis runs off the main thread.  
- Cloud Anchor host/resolve is time-bounded.

## Backend modules

| Module | Role |
|--------|------|
| `routers/auth.py` | Register / login, JWT issue |
| `routers/sessions.py` | Session CRUD + bulk sample upload |
| `heatmap.py` | Server-side interpolation helpers |
| `models.py` | SQLAlchemy / PostGIS entities |

See [API.md](API.md) for endpoint details.
