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

    @Query("SELECT COUNT(*) FROM speed_tests WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int
}
