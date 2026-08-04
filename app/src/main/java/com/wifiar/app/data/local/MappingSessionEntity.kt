package com.wifiar.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Metadata for a mapping walkthrough session.
 */
@Entity(tableName = "mapping_sessions")
data class MappingSessionEntity(
    @PrimaryKey val sessionId: String,
    val locationName: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    /** True after a successful full upload to the backend. */
    val synced: Boolean = false,
    /** Server-side UUID returned by POST /sessions (nullable until synced). */
    val remoteSessionId: String? = null,
    /**
     * ARCore Cloud Anchor ID for multi-day origin continuity (Part 10).
     * Null when not hosted or API key unavailable.
     */
    val cloudAnchorId: String? = null,
)

/**
 * Session row with aggregated sample count for history UI.
 */
data class SessionSummary(
    val sessionId: String,
    val locationName: String,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val sampleCount: Int,
    val synced: Boolean,
    val cloudAnchorId: String? = null,
)
