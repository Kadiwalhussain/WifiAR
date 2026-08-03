package com.wifiar.app.data.speedtest

import android.util.Log
import com.wifiar.app.AppConfig

/**
 * Option A — Ookla Speedtest SDK integration point.
 *
 * The official Ookla Speedtest SDK is a **partner-only** Android package
 * (not published on public Maven Central). Integration steps for a licensed
 * project:
 *
 * 1. Obtain a partner account + API key from Ookla.
 * 2. Drop the SDK AAR into `app/libs/` and add
 *    `implementation(files("libs/speedtestsdk-android.aar"))`.
 * 3. Set [AppConfig.OOKLA_API_KEY] (or inject via BuildConfig).
 * 4. Set [AppConfig.SPEED_TEST_BACKEND] = [com.wifiar.app.SpeedTestBackend.OOKLA].
 * 5. Replace the body of [run] with the SDK's configure → start → result callbacks
 *    (typical package historically: `com.ookla.speedtest.sdk`).
 *
 * Until the AAR + key are present this engine fails with a clear
 * [SpeedTestError.BackendUnavailable] so demos can flip back to
 * [ThroughputSpeedTestEngine] instantly.
 */
class OoklaSpeedTestEngine(
    private val apiKey: String = AppConfig.OOKLA_API_KEY,
) : SpeedTestEngine {

    override val name: String = "ookla-sdk"

    override suspend fun run(): SpeedTestResult {
        if (apiKey.isBlank()) {
            throw SpeedTestError.BackendUnavailable(
                "Ookla backend selected but OOKLA_API_KEY is empty. " +
                    "Add a partner key in AppConfig, or switch SPEED_TEST_BACKEND to THROUGHPUT.",
            )
        }

        // Probe for the proprietary SDK on the classpath without a hard dependency.
        val sdkPresent = runCatching {
            Class.forName("com.ookla.speedtest.sdk.Speedtest")
            true
        }.getOrDefault(false)

        if (!sdkPresent) {
            Log.w(TAG, "Ookla SDK class not found on classpath")
            throw SpeedTestError.BackendUnavailable(
                "Ookla Speedtest SDK is not linked. Add the partner AAR to app/libs/ " +
                    "or set AppConfig.SPEED_TEST_BACKEND = THROUGHPUT for the built-in test.",
            )
        }

        // When the real SDK is present, wire it here. Left as an explicit failure
        // so a partial integration cannot return fake numbers.
        throw SpeedTestError.BackendUnavailable(
            "Ookla SDK detected but run() not fully wired for this build. " +
                "Complete the SDK callback mapping in OoklaSpeedTestEngine.",
        )
    }

    companion object {
        private const val TAG = "OoklaSpeedTest"
    }
}
