from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel, EmailStr, Field


# ── Auth ─────────────────────────────────────────────────────────────────────


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    id: UUID
    email: EmailStr
    created_at: datetime

    model_config = {"from_attributes": True}


# ── Points ───────────────────────────────────────────────────────────────────


class RssiPointIn(BaseModel):
    pose_x: float
    pose_y: float
    pose_z: float
    ssid: str = ""
    bssid: str = ""
    rssi_dbm: int
    recorded_at_ms: int


class SpeedTestPointIn(BaseModel):
    pose_x: float
    pose_y: float
    pose_z: float
    download_mbps: float
    upload_mbps: float
    ping_ms: int
    recorded_at_ms: int


class BulkRssiUpload(BaseModel):
    points: list[RssiPointIn]


class BulkSpeedTestUpload(BaseModel):
    points: list[SpeedTestPointIn]


class BulkUploadResult(BaseModel):
    inserted: int


# ── Sessions ─────────────────────────────────────────────────────────────────


class SessionCreate(BaseModel):
    location_name: str = Field(min_length=1, max_length=500)
    client_session_id: str | None = None
    origin_metadata: dict[str, Any] | None = None
    created_at_ms: int | None = None


class RssiPointOut(BaseModel):
    id: UUID
    pose_x: float
    pose_y: float
    pose_z: float
    ssid: str
    bssid: str
    rssi_dbm: int
    recorded_at: datetime


class SpeedTestPointOut(BaseModel):
    id: UUID
    pose_x: float
    pose_y: float
    pose_z: float
    download_mbps: float
    upload_mbps: float
    ping_ms: int
    recorded_at: datetime


class SessionSummaryOut(BaseModel):
    id: UUID
    location_name: str
    created_at: datetime
    client_session_id: str | None
    rssi_count: int
    speed_test_count: int


class SessionDetailOut(BaseModel):
    id: UUID
    location_name: str
    created_at: datetime
    client_session_id: str | None
    origin_metadata: dict[str, Any] | None
    rssi_points: list[RssiPointOut]
    speed_test_points: list[SpeedTestPointOut]


# ── Heatmap ──────────────────────────────────────────────────────────────────


class HeatmapGridOut(BaseModel):
    method: str
    min_x: float
    max_x: float
    min_z: float
    max_z: float
    cell_size: float
    cols: int
    rows: int
    values: list[float | None]  # row-major; null where prediction invalid
    sample_count: int
    compute_ms: int
