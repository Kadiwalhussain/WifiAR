# Setup guide

## Prerequisites

- Android Studio (Giraffe or newer recommended) with **JDK 17**
- Physical phone with **ARCore** support, Android **8.0 (API 26)+**
- USB debugging enabled
- (Optional) Docker + Python 3.10+ for the backend

## Clone and open

```bash
git clone https://github.com/Kadiwalhussain/WifiAR.git
cd WifiAR
```

Open the folder in Android Studio and let Gradle sync finish.

## Run on a device

1. Connect the phone via USB (or wireless debugging).  
2. Select the **app** run configuration.  
3. Press **Run**.  
4. Grant **Location**, **Camera**, and on Android 13+ **Nearby Wi‑Fi devices**.

Walk a real room — not an emulator — for reliable scans and tracking.

## Configure secrets (optional)

Create or edit `local.properties` in the project root (this file is gitignored):

```properties
sdk.dir=/path/to/Android/sdk
arcore.api.key=YOUR_ARCORE_API_KEY
```

| Key | Purpose |
|-----|---------|
| `arcore.api.key` | Enables ARCore Cloud Anchors for multi-day origin alignment |

## Point the app at a backend

Edit `app/src/main/java/com/wifiar/app/AppConfig.kt`:

```kotlin
const val API_BASE_URL: String = "http://YOUR_LAN_IP:8000/"
```

| Environment | Typical URL |
|-------------|-------------|
| Android emulator → host machine | `http://10.0.2.2:8000/` |
| Physical phone on same Wi‑Fi | `http://192.168.x.x:8000/` |

## Start the backend

```bash
cd wifiar-backend
docker compose up -d
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Verify:

- Health: http://localhost:8000/health  
- OpenAPI: http://localhost:8000/docs  

## Build release

```bash
./gradlew :app:assembleRelease
```

Sign the APK/AAB with your own keystore before distributing outside debug installs.

## Troubleshooting

| Symptom | What to try |
|---------|-------------|
| No scan results | Enable system Location + Wi‑Fi; grant app location permission |
| AR never tracks | Use a well-lit textured room; avoid blank walls |
| Sync fails | Confirm `API_BASE_URL`, phone and PC on same network, backend running |
| Cloud Anchor host fails | Check API key in `local.properties` and ARCore API enabled in Google Cloud |

More product context: [USER_GUIDE.md](USER_GUIDE.md) · [ARCHITECTURE.md](ARCHITECTURE.md)
