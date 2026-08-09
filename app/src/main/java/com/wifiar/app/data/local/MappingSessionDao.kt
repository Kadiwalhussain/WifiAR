package com.wifiar.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MappingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: MappingSessionEntity)

    @Query(
        """
        UPDATE mapping_sessions
        SET endTimeMs = :endTimeMs
        WHERE sessionId = :sessionId
        """,
    )
    suspend fun markEnded(sessionId: String, endTimeMs: Long)

    @Query(
        """
        UPDATE mapping_sessions
        SET endTimeMs = NULL
        WHERE sessionId = :sessionId
        """,
    )
    suspend fun reopenSession(sessionId: String)

    @Query(
        """
        UPDATE mapping_sessions
        SET synced = :synced, remoteSessionId = :remoteSessionId
        WHERE sessionId = :sessionId
        """,
    )
    suspend fun markSynced(sessionId: String, remoteSessionId: String, synced: Boolean = true)

    @Query(
        """
        UPDATE mapping_sessions
        SET cloudAnchorId = :cloudAnchorId
        WHERE sessionId = :sessionId
        """,
    )
    suspend fun setCloudAnchorId(sessionId: String, cloudAnchorId: String)

    @Query("SELECT * FROM mapping_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): MappingSessionEntity?

    @Query(
        """
        SELECT * FROM mapping_sessions
        WHERE endTimeMs IS NOT NULL AND synced = 0
        ORDER BY endTimeMs ASC
        """,
    )
    suspend fun getPendingSyncSessions(): List<MappingSessionEntity>

    @Query(
        """
        SELECT * FROM mapping_sessions
        WHERE cloudAnchorId IS NOT NULL AND cloudAnchorId != ''
        ORDER BY startTimeMs DESC
        """,
    )
    suspend fun getSessionsWithCloudAnchors(): List<MappingSessionEntity>

    /**
     * Ended sessions that can be reopened for multi-day mapping (Part 10).
     * Prefer exact location match when [locationName] is non-blank.
     */
    @Query(
        """
        SELECT * FROM mapping_sessions
        WHERE endTimeMs IS NOT NULL
          AND (
            :locationName = ''
            OR LOWER(locationName) = LOWER(:locationName)
          )
        ORDER BY startTimeMs DESC
        LIMIT 20
        """,
    )
    suspend fun getResumableSessions(locationName: String): List<MappingSessionEntity>

    /** All sessions newest first — used for export / clear-history UX. */
    @Query(
        """
        SELECT * FROM mapping_sessions
        ORDER BY startTimeMs DESC
        LIMIT 50
        """,
    )
    suspend fun getRecentSessions(): List<MappingSessionEntity>

    @Query(
        """
        SELECT s.sessionId AS sessionId,
               s.locationName AS locationName,
               s.startTimeMs AS startTimeMs,
               s.endTimeMs AS endTimeMs,
               COUNT(r.id) AS sampleCount,
               s.synced AS synced,
               s.cloudAnchorId AS cloudAnchorId
        FROM mapping_sessions AS s
        LEFT JOIN rssi_samples AS r ON r.sessionId = s.sessionId
        GROUP BY s.sessionId
        ORDER BY s.startTimeMs DESC
        """,
    )
    fun observeSessionSummaries(): Flow<List<SessionSummary>>

    @Query("DELETE FROM mapping_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM mapping_sessions")
    suspend fun deleteAll()
}
