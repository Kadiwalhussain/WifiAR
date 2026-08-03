"""initial schema with PostGIS tables

Revision ID: 001_initial
Revises:
Create Date: 2026-08-03

"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from geoalchemy2 import Geometry
from sqlalchemy.dialects import postgresql

revision: str = "001_initial"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS postgis")

    op.create_table(
        "users",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("email", sa.String(length=320), nullable=False),
        sa.Column("hashed_password", sa.String(length=255), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_users_email", "users", ["email"], unique=True)

    op.create_table(
        "heatmap_sessions",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("location_name", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("client_session_id", sa.String(length=64), nullable=True),
        sa.Column("origin_metadata", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
    )
    op.create_index("ix_heatmap_sessions_user_id", "heatmap_sessions", ["user_id"])
    op.create_index("ix_heatmap_sessions_client_session_id", "heatmap_sessions", ["client_session_id"])

    op.create_table(
        "rssi_points",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "session_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("heatmap_sessions.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("geom", Geometry(geometry_type="POINTZ", srid=0), nullable=False),
        sa.Column("ssid", sa.Text(), nullable=False),
        sa.Column("bssid", sa.Text(), nullable=False),
        sa.Column("rssi_dbm", sa.Integer(), nullable=False),
        sa.Column("recorded_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_rssi_points_session_id", "rssi_points", ["session_id"])

    op.create_table(
        "speed_test_points",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "session_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("heatmap_sessions.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("geom", Geometry(geometry_type="POINTZ", srid=0), nullable=False),
        sa.Column("download_mbps", sa.Float(), nullable=False),
        sa.Column("upload_mbps", sa.Float(), nullable=False),
        sa.Column("ping_ms", sa.Integer(), nullable=False),
        sa.Column("recorded_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_speed_test_points_session_id", "speed_test_points", ["session_id"])


def downgrade() -> None:
    op.drop_table("speed_test_points")
    op.drop_table("rssi_points")
    op.drop_table("heatmap_sessions")
    op.drop_table("users")
