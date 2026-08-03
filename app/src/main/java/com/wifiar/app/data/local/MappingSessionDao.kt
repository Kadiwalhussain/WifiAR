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
        SET synced = :synced, remoteSessionId = :remoteSessionId
        WHERE sessionId = :sessionId
        """,
    )
    suspend fun markSynced(sessionId: String, remoteSessionId: String, synced: Boolean = true)

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
        SELECT s.sessionId AS sessionId,
               s.locationName AS locationName,
               s.startTimeMs AS startTimeMs,
               s.endTimeMs AS endTimeMs,
               COUNT(r.id) AS sampleCount,
               s.synced AS synced
        FROM mapping_sessions AS s
        LEFT JOIN rssi_samples AS r ON r.sessionId = s.sessionId
        GROUP BY s.sessionId
        ORDER BY s.startTimeMs DESC
        """,
    )
    fun observeSessionSummaries(): Flow<List<SessionSummary>>

    @Query("DELETE FROM mapping_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
