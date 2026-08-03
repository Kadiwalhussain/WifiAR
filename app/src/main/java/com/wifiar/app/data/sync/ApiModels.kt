package com.wifiar.app.data.sync

import com.squareup.moshi.Json

data class RegisterRequest(
    val email: String,
    val password: String,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String = "bearer",
)

data class UserOut(
    val id: String,
    val email: String,
    @Json(name = "created_at") val createdAt: String? = null,
)

data class SessionCreateRequest(
    @Json(name = "location_name") val locationName: String,
    @Json(name = "client_session_id") val clientSessionId: String? = null,
    @Json(name = "origin_metadata") val originMetadata: Map<String, String>? = null,
    @Json(name = "created_at_ms") val createdAtMs: Long? = null,
)

data class SessionSummaryOut(
    val id: String,
    @Json(name = "location_name") val locationName: String,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "client_session_id") val clientSessionId: String? = null,
    @Json(name = "rssi_count") val rssiCount: Int = 0,
    @Json(name = "speed_test_count") val speedTestCount: Int = 0,
)

data class RssiPointIn(
    @Json(name = "pose_x") val poseX: Float,
    @Json(name = "pose_y") val poseY: Float,
    @Json(name = "pose_z") val poseZ: Float,
    val ssid: String = "",
    val bssid: String = "",
    @Json(name = "rssi_dbm") val rssiDbm: Int,
    @Json(name = "recorded_at_ms") val recordedAtMs: Long,
)

data class BulkRssiUpload(
    val points: List<RssiPointIn>,
)

data class SpeedTestPointIn(
    @Json(name = "pose_x") val poseX: Float,
    @Json(name = "pose_y") val poseY: Float,
    @Json(name = "pose_z") val poseZ: Float,
    @Json(name = "download_mbps") val downloadMbps: Float,
    @Json(name = "upload_mbps") val uploadMbps: Float,
    @Json(name = "ping_ms") val pingMs: Int,
    @Json(name = "recorded_at_ms") val recordedAtMs: Long,
)

data class BulkSpeedTestUpload(
    val points: List<SpeedTestPointIn>,
)

data class BulkUploadResult(
    val inserted: Int,
)

data class HeatmapGridOut(
    val method: String,
    @Json(name = "min_x") val minX: Double,
    @Json(name = "max_x") val maxX: Double,
    @Json(name = "min_z") val minZ: Double,
    @Json(name = "max_z") val maxZ: Double,
    @Json(name = "cell_size") val cellSize: Double,
    val cols: Int,
    val rows: Int,
    val values: List<Double?>,
    @Json(name = "sample_count") val sampleCount: Int,
    @Json(name = "compute_ms") val computeMs: Int,
)
