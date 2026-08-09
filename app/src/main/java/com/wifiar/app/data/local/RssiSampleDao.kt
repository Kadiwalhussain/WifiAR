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
}
