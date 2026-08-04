# Changelog

All notable changes to WifiAR are documented here.

## [1.0.0] — 2026-08-04

### Added

- Live AR Wi‑Fi mapping with IDW heatmaps  
- Multi-AP scanning and per-network comparison  
- Dead-zone detection and AR markers  
- Speed-test checkpoints  
- Log-distance path-loss router placement suggestions  
- Session history with PNG/CSV export and system share sheet  
- Optional FastAPI + PostGIS sync with JWT  
- Optional ARCore Cloud Anchors for multi-day origin continuity  
- Onboarding flow and settings (RSSI thresholds, grid, path-loss)  
- Brand adaptive launcher icons and navigation icons  
- Project documentation under `docs/`  

### Performance

- Spatial downsampling of AR sample spheres  
- Gated heatmap recompute  
- Time-bounded Cloud Anchor host/resolve  

### UI

- Compact Material 3 type scale  
- Neon dark theme and glass-style HUD  
- Animated tab transitions and empty states  
