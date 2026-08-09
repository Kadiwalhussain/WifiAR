package com.wifiar.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RssiSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: RssiSampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<RssiSampleEntity>): List<Long>

    @Query("SELECT * FROM rssi_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getAllForSession(sessionId: String): Flow<List<RssiSampleEntity>>

    @Query("SELECT * FROM rssi_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getAllForSessionOnce(sessionId: String): List<RssiSampleEntity>

    @Query("DELETE FROM rssi_samples WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM rssi_samples")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM rssi_samples WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    /** Samples in a time window (history chart / summary). */
    @Query(
        """
        SELECT * FROM rssi_samples
        WHERE timestampMs >= :sinceMs
        ORDER BY timestampMs ASC
        """,
    )
    suspend fun getSince(sinceMs: Long): List<RssiSampleEntity>

    /**
     * Samples for many sessions. **Never call with an empty list** — Room emits
     * invalid `IN ()` SQL. Prefer [getForSessionsSafe].
     */
    @Query(
        """
        SELECT * FROM rssi_samples
        WHERE sessionId IN (:sessionIds)
        ORDER BY timestampMs ASC
        """,
    )
    suspend fun getForSessions(sessionIds: List<String>): List<RssiSampleEntity>

    suspend fun getForSessionsSafe(sessionIds: List<String>): List<RssiSampleEntity> {
        if (sessionIds.isEmpty()) return emptyList()
        return getForSessions(sessionIds)
    }

    @Query("SELECT MAX(rssiDbm) FROM rssi_samples WHERE sessionId = :sessionId")
    suspend fun maxRssiForSession(sessionId: String): Int?

    @Query(
        """
        SELECT ssid FROM rssi_samples
        WHERE sessionId = :sessionId AND ssid != ''
        GROUP BY ssid
        ORDER BY COUNT(*) DESC
        LIMIT 1
        """,
    )
    suspend fun dominantSsidForSession(sessionId: String): String?
}
