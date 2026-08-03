package com.wifiar.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Speed-test checkpoint linked to a mapping session + AR pose.
 */
@Entity(
    tableName = "speed_tests",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "timestampMs"]),
    ],
)
data class SpeedTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val poseX: Float,
    val poseY: Float,
    val poseZ: Float,
    val downloadMbps: Float,
    val uploadMbps: Float,
    val pingMs: Int,
    val timestampMs: Long,
    /** Backend id used for the measurement (e.g. throughput-http, ookla-sdk). */
    val backend: String = "throughput-http",
)
