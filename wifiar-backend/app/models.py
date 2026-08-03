import uuid
from datetime import datetime, timezone

from geoalchemy2 import Geometry
from sqlalchemy import DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True, nullable=False)
    hashed_password: Mapped[str] = mapped_column(String(255), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=_utcnow, nullable=False
    )

    sessions: Mapped[list["HeatmapSession"]] = relationship(back_populates="user")


class HeatmapSession(Base):
    __tablename__ = "heatmap_sessions"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    location_name: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=_utcnow, nullable=False
    )
    # Client-local session UUID for idempotent sync.
    client_session_id: Mapped[str | None] = mapped_column(String(64), index=True, nullable=True)
    origin_metadata: Mapped[dict | None] = mapped_column(JSONB, nullable=True)

    user: Mapped[User] = relationship(back_populates="sessions")
    rssi_points: Mapped[list["RssiPoint"]] = relationship(
        back_populates="session", cascade="all, delete-orphan"
    )
    speed_test_points: Mapped[list["SpeedTestPoint"]] = relationship(
        back_populates="session", cascade="all, delete-orphan"
    )


class RssiPoint(Base):
    __tablename__ = "rssi_points"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("heatmap_sessions.id", ondelete="CASCADE"),
        index=True,
        nullable=False,
    )
    # Local AR coordinates (not WGS84) — SRID 0 arbitrary CRS.
    geom = mapped_column(Geometry(geometry_type="POINTZ", srid=0), nullable=False)
    ssid: Mapped[str] = mapped_column(Text, nullable=False, default="")
    bssid: Mapped[str] = mapped_column(Text, nullable=False, default="")
    rssi_dbm: Mapped[int] = mapped_column(Integer, nullable=False)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    session: Mapped[HeatmapSession] = relationship(back_populates="rssi_points")


class SpeedTestPoint(Base):
    __tablename__ = "speed_test_points"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("heatmap_sessions.id", ondelete="CASCADE"),
        index=True,
        nullable=False,
    )
    geom = mapped_column(Geometry(geometry_type="POINTZ", srid=0), nullable=False)
    download_mbps: Mapped[float] = mapped_column(Float, nullable=False)
    upload_mbps: Mapped[float] = mapped_column(Float, nullable=False)
    ping_ms: Mapped[int] = mapped_column(Integer, nullable=False)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    session: Mapped[HeatmapSession] = relationship(back_populates="speed_test_points")
