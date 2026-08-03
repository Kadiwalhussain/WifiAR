# WifiAR Backend

FastAPI + PostgreSQL/PostGIS API for session sync, auth, and server-side GP heatmaps.

## Quick start

```bash
# 1. PostGIS
docker compose up -d

# 2. Python env
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# 3. Migrations (optional if using auto create_all on startup)
alembic upgrade head

# 4. Run API
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Health: http://localhost:8000/health  
- Docs: http://localhost:8000/docs  

Default DB: `postgresql://wifiar:wifiar@localhost:5432/wifiar`

## Auth

- `POST /auth/register` `{ "email", "password" }`
- `POST /auth/login` → `{ "access_token", "token_type": "bearer" }`
- `GET /auth/me` (Bearer)

## Sessions (Bearer required)

- `POST /sessions` — create (idempotent via `client_session_id`)
- `POST /sessions/{id}/points` — bulk RSSI upload
- `POST /sessions/{id}/speedtests` — bulk speed-test upload
- `GET /sessions` / `GET /sessions/{id}` / `DELETE /sessions/{id}`
- `GET /sessions/{id}/heatmap` — Gaussian Process grid (smoother than on-device IDW)
