# Backend API

Base URL defaults to `http://localhost:8000` when running locally.

Interactive docs: `/docs` (Swagger UI).

## Authentication

### Register

```http
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "min8chars"
}
```

### Login

```http
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "min8chars"
}
```

Response includes a JWT access token. Send it as:

```http
Authorization: Bearer <token>
```

## Sessions

### Create session

```http
POST /sessions
Authorization: Bearer <token>
Content-Type: application/json

{
  "location_name": "Home - Living Room",
  "started_at": "2026-08-04T12:00:00Z"
}
```

### Bulk upload RSSI samples

```http
POST /sessions/{session_id}/samples
Authorization: Bearer <token>
Content-Type: application/json

{
  "samples": [
    {
      "timestamp_ms": 1720000000000,
      "pose_x": 1.2,
      "pose_y": 1.5,
      "pose_z": 0.3,
      "ssid": "HomeWiFi",
      "bssid": "aa:bb:cc:dd:ee:ff",
      "rssi_dbm": -62,
      "frequency_mhz": 5180
    }
  ]
}
```

### Bulk upload speed tests

```http
POST /sessions/{session_id}/speed-tests
Authorization: Bearer <token>
```

### List sessions

```http
GET /sessions
Authorization: Bearer <token>
```

### Heatmap

```http
GET /sessions/{session_id}/heatmap
Authorization: Bearer <token>
```

Returns a server-side interpolated grid (when enough points exist).

## Health

```http
GET /health
```

## Notes

- Coordinates are **local AR metres**, not WGS84.  
- Bulk endpoints are preferred for phone uploads (chunked by the Android client).  
- CORS is configurable for local development.
