# User guide

## First launch

1. Read the short onboarding pages.  
2. Allow **Location** (required by Android for Wi‑Fi RSSI).  
3. Allow **Camera** (required for AR).  
4. On Android 13+, allow **Nearby Wi‑Fi devices** if prompted.

## Mapping a room

1. Open the **Map** tab.  
2. Tap **Start Session** and name the space (e.g. `Office Floor 2`).  
3. If you mapped this place before, choose **Resume previous** when offered.  
4. Walk slowly. Cover edges and corners. Pause a few seconds so scans can finish.  
5. Use the segmented control:
   - **Points** — colored spheres at sample poses  
   - **Heatmap** — interpolated floor plane  
   - **Both** — spheres + plane  
6. Tap **Run Speed Test Here** for a throughput checkpoint (needs stable tracking).  
7. Tap **End Session** when finished.

## Reading the colors

Defaults (adjustable in **Settings**):

| Color | Meaning |
|-------|---------|
| Green | Strong signal |
| Amber | Medium |
| Magenta / purple | Weak |
| Dark red | Dead zone (at or below threshold) |

## History

- Open a session to review samples, dead zones, and speed tests.  
- **Compare networks** ranks SSIDs seen during the walk.  
- **Suggest router placement** ranks candidate AP positions (heuristic).  
- **Export & share** builds a PNG heatmap + CSV and opens the system share sheet.

## Account

Sign in to upload ended sessions to your backend when online. Pending uploads retry via background work.

## Settings

- RSSI color thresholds  
- Dead-zone cutoff  
- Path-loss preset (indoor vs open)  
- Heatmap grid cell size  
- Cloud Anchors privacy acknowledgement  

## Tips for better maps

- Prefer daylight or well-lit rooms with visual texture.  
- Avoid spinning the phone quickly.  
- Dense multi-AP environments produce more samples; the AR view down-samples spheres for smoothness while history keeps full data.  
- Name sessions consistently so resume and history stay organized.
