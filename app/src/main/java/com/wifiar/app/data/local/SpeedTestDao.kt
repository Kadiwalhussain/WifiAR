package com.wifiar.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SpeedTestEntity): Long

    @Query("SELECT * FROM speed_tests WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getAllForSession(sessionId: String): Flow<List<SpeedTestEntity>>

    @Query("SELECT * FROM speed_tests WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getAllForSessionOnce(sessionId: String): List<SpeedTestEntity>

    @Query("DELETE FROM speed_tests WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM speed_tests")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM speed_tests WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("SELECT * FROM speed_tests ORDER BY timestampMs DESC LIMIT 200")
    fun observeAllRecent(): Flow<List<SpeedTestEntity>>

    @Query(
        """
        SELECT * FROM speed_tests
        WHERE timestampMs >= :sinceMs
        ORDER BY timestampMs DESC
        """,
    )
    suspend fun getSince(sinceMs: Long): List<SpeedTestEntity>

    @Query("SELECT MAX(downloadMbps) FROM speed_tests WHERE timestampMs >= :sinceMs")
    suspend fun maxDownloadSince(sinceMs: Long): Float?
}
