package com.wifiar.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fused WiFi sample: RSSI + AR pose at (approximately) the same moment.
 */
@Entity(
    tableName = "rssi_samples",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "timestampMs"]),
    ],
)
data class RssiSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val timestampMs: Long,
    val poseX: Float,
    val poseY: Float,
    val poseZ: Float,
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
)
