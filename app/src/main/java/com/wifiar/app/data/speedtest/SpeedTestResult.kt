package com.wifiar.app.data.speedtest

/**
 * Successful speed-test measurement at a single point in time.
 */
data class SpeedTestResult(
    val downloadMbps: Float,
    val uploadMbps: Float,
    val pingMs: Int,
    val timestampMs: Long,
    val backend: String,
)

/**
 * Typed failures so the UI can show a clear message (no crashes).
 */
sealed class SpeedTestError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoInternet(message: String = "No internet connection") : SpeedTestError(message)
    class ServerUnreachable(message: String, cause: Throwable? = null) :
        SpeedTestError(message, cause)

    class Cancelled(message: String = "Speed test cancelled") : SpeedTestError(message)
    class BackendUnavailable(message: String) : SpeedTestError(message)
    class Unknown(message: String, cause: Throwable? = null) : SpeedTestError(message, cause)
}

/**
 * Outcome of [SpeedTestManager.runSpeedTest].
 */
sealed class SpeedTestOutcome {
    data class Success(val result: SpeedTestResult) : SpeedTestOutcome()
    data class Failure(val error: SpeedTestError) : SpeedTestOutcome()
}
