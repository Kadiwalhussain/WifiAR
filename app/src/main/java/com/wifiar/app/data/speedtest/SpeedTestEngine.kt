package com.wifiar.app.data.speedtest

/**
 * Pluggable speed-test implementation (Ookla vs HTTP throughput).
 */
interface SpeedTestEngine {
    val name: String

    /**
     * Run a full ping + download + upload measurement.
     * Throws [SpeedTestError] on failure; never crashes the process.
     */
    suspend fun run(): SpeedTestResult
}
