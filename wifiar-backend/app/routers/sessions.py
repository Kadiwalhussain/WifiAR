from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from geoalchemy2.elements import WKTElement
from geoalchemy2.shape import to_shape
from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from app.database import get_db
from app.deps import get_current_user
from app.heatmap import recompute_heatmap_gp
from app.models import HeatmapSession, RssiPoint, SpeedTestPoint, User
from app.schemas import (
    BulkRssiUpload,
    BulkSpeedTestUpload,
    BulkUploadResult,
    HeatmapGridOut,
    RssiPointOut,
    SessionCreate,
    SessionDetailOut,
    SessionSummaryOut,
    SpeedTestPointOut,
)

router = APIRouter(prefix="/sessions", tags=["sessions"])


def _ms_to_dt(ms: int) -> datetime:
    return datetime.fromtimestamp(ms / 1000.0, tz=timezone.utc)


def _point_z(x: float, y: float, z: float) -> WKTElement:
    return WKTElement(f"POINT Z ({x} {y} {z})", srid=0)


def _xyz_from_geom(geom) -> tuple[float, float, float]:
    shape = to_shape(geom)
    # PointZ: x, y, z — we store pose as x, y=height, z=depth
    coords = shape.coords[0]
    if len(coords) >= 3:
        return float(coords[0]), float(coords[1]), float(coords[2])
    return float(coords[0]), 0.0, float(coords[1])


def _get_owned_session(db: Session, session_id: UUID, user: User) -> HeatmapSession:
    session = db.get(HeatmapSession, session_id)
    if session is None or session.user_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")
    return session


@router.post("", response_model=SessionSummaryOut, status_code=status.HTTP_201_CREATED)
def create_session(
    body: SessionCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> SessionSummaryOut:
    # Idempotent sync: if client_session_id already uploaded, return existing.
    if body.client_session_id:
        existing = db.scalar(
            select(HeatmapSession).where(
                HeatmapSession.user_id == user.id,
                HeatmapSession.client_session_id == body.client_session_id,
            )
        )
        if existing:
            rssi_count = db.scalar(
                select(func.count()).select_from(RssiPoint).where(RssiPoint.session_id == existing.id)
            ) or 0
            st_count = db.scalar(
                select(func.count())
                .select_from(SpeedTestPoint)
                .where(SpeedTestPoint.session_id == existing.id)
            ) or 0
            return SessionSummaryOut(
                id=existing.id,
                location_name=existing.location_name,
                created_at=existing.created_at,
                client_session_id=existing.client_session_id,
                rssi_count=int(rssi_count),
                speed_test_count=int(st_count),
            )

    created_at = (
        _ms_to_dt(body.created_at_ms) if body.created_at_ms is not None else datetime.now(timezone.utc)
    )
    session = HeatmapSession(
        user_id=user.id,
        location_name=body.location_name.strip(),
        client_session_id=body.client_session_id,
        origin_metadata=body.origin_metadata,
        created_at=created_at,
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    return SessionSummaryOut(
        id=session.id,
        location_name=session.location_name,
        created_at=session.created_at,
        client_session_id=session.client_session_id,
        rssi_count=0,
        speed_test_count=0,
    )


@router.post("/{session_id}/points", response_model=BulkUploadResult)
def upload_rssi_points(
    session_id: UUID,
    body: BulkRssiUpload,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> BulkUploadResult:
    session = _get_owned_session(db, session_id, user)
    rows = []
    for p in body.points:
        rows.append(
            RssiPoint(
                session_id=session.id,
                geom=_point_z(p.pose_x, p.pose_y, p.pose_z),
                ssid=p.ssid or "",
                bssid=p.bssid or "",
                rssi_dbm=p.rssi_dbm,
                recorded_at=_ms_to_dt(p.recorded_at_ms),
            )
        )
    db.add_all(rows)
    db.commit()
    return BulkUploadResult(inserted=len(rows))


@router.post("/{session_id}/speedtests", response_model=BulkUploadResult)
def upload_speed_tests(
    session_id: UUID,
    body: BulkSpeedTestUpload,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> BulkUploadResult:
    session = _get_owned_session(db, session_id, user)
    rows = []
    for p in body.points:
        rows.append(
            SpeedTestPoint(
                session_id=session.id,
                geom=_point_z(p.pose_x, p.pose_y, p.pose_z),
                download_mbps=p.download_mbps,
                upload_mbps=p.upload_mbps,
                ping_ms=p.ping_ms,
                recorded_at=_ms_to_dt(p.recorded_at_ms),
            )
        )
    db.add_all(rows)
    db.commit()
    return BulkUploadResult(inserted=len(rows))


@router.get("", response_model=list[SessionSummaryOut])
def list_sessions(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> list[SessionSummaryOut]:
    sessions = db.scalars(
        select(HeatmapSession)
        .where(HeatmapSession.user_id == user.id)
        .order_by(HeatmapSession.created_at.desc())
    ).all()
    out: list[SessionSummaryOut] = []
    for s in sessions:
        rssi_count = db.scalar(
            select(func.count()).select_from(RssiPoint).where(RssiPoint.session_id == s.id)
        ) or 0
        st_count = db.scalar(
            select(func.count()).select_from(SpeedTestPoint).where(SpeedTestPoint.session_id == s.id)
        ) or 0
        out.append(
            SessionSummaryOut(
                id=s.id,
                location_name=s.location_name,
                created_at=s.created_at,
                client_session_id=s.client_session_id,
                rssi_count=int(rssi_count),
                speed_test_count=int(st_count),
            )
        )
    return out


@router.get("/{session_id}", response_model=SessionDetailOut)
def get_session(
    session_id: UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> SessionDetailOut:
    session = db.scalar(
        select(HeatmapSession)
        .options(
            selectinload(HeatmapSession.rssi_points),
            selectinload(HeatmapSession.speed_test_points),
        )
        .where(HeatmapSession.id == session_id, HeatmapSession.user_id == user.id)
    )
    if session is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    rssi_out: list[RssiPointOut] = []
    for p in session.rssi_points:
        x, y, z = _xyz_from_geom(p.geom)
        rssi_out.append(
            RssiPointOut(
                id=p.id,
                pose_x=x,
                pose_y=y,
                pose_z=z,
                ssid=p.ssid,
                bssid=p.bssid,
                rssi_dbm=p.rssi_dbm,
                recorded_at=p.recorded_at,
            )
        )

    st_out: list[SpeedTestPointOut] = []
    for p in session.speed_test_points:
        x, y, z = _xyz_from_geom(p.geom)
        st_out.append(
            SpeedTestPointOut(
                id=p.id,
                pose_x=x,
                pose_y=y,
                pose_z=z,
                download_mbps=p.download_mbps,
                upload_mbps=p.upload_mbps,
                ping_ms=p.ping_ms,
                recorded_at=p.recorded_at,
            )
        )

    return SessionDetailOut(
        id=session.id,
        location_name=session.location_name,
        created_at=session.created_at,
        client_session_id=session.client_session_id,
        origin_metadata=session.origin_metadata,
        rssi_points=rssi_out,
        speed_test_points=st_out,
    )


@router.delete("/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_session(
    session_id: UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> None:
    session = _get_owned_session(db, session_id, user)
    db.delete(session)
    db.commit()


@router.get("/{session_id}/heatmap", response_model=HeatmapGridOut)
def get_heatmap(
    session_id: UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> HeatmapGridOut:
    session = _get_owned_session(db, session_id, user)
    points = db.scalars(select(RssiPoint).where(RssiPoint.session_id == session.id)).all()
    samples: list[tuple[float, float, float]] = []
    for p in points:
        x, _y, z = _xyz_from_geom(p.geom)
        samples.append((x, z, float(p.rssi_dbm)))
    return recompute_heatmap_gp(samples)
