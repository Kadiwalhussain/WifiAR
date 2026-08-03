package com.wifiar.app.scanner

/**
 * Normalized WiFi scan sample used across the app.
 */
data class RssiSample(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val timestampMs: Long,
    val capabilities: String,
)
