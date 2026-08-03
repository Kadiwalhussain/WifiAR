package com.wifiar.app.data.speedtest

import android.util.Log
import com.wifiar.app.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.system.measureNanoTime

/**
 * Option B — lightweight HTTP throughput test (iPerf-like idea without a
 * custom server): download a known byte count, upload a known payload, and
 * measure HTTP RTT for ping.
 *
 * Uses Cloudflare's public speed endpoints (no API key). Suitable for demos
 * and college projects; results are directionally comparable to consumer
 * speed-test apps on the same network.
 */
class ThroughputSpeedTestEngine(
    private val downloadBytes: Int = AppConfig.SPEED_TEST_DOWNLOAD_BYTES,
    private val uploadBytes: Int = AppConfig.SPEED_TEST_UPLOAD_BYTES,
    private val pingSamples: Int = AppConfig.SPEED_TEST_PING_SAMPLES,
    private val timeoutMs: Int = AppConfig.SPEED_TEST_TIMEOUT_MS,
) : SpeedTestEngine {

    override val name: String = "throughput-http"

    override suspend fun run(): SpeedTestResult = withContext(Dispatchers.IO) {
        try {
            val ping = measurePingMs()
            coroutineContext.ensureActive()
            val down = measureDownloadMbps()
            coroutineContext.ensureActive()
            val up = measureUploadMbps()
            SpeedTestResult(
                downloadMbps = down,
                uploadMbps = up,
                pingMs = ping,
                timestampMs = System.currentTimeMillis(),
                backend = name,
            )
        } catch (e: SpeedTestError) {
            throw e
        } catch (e: UnknownHostException) {
            throw SpeedTestError.NoInternet("DNS failed — check internet: ${e.message}")
        } catch (e: java.net.ConnectException) {
            throw SpeedTestError.ServerUnreachable("Cannot reach test server", e)
        } catch (e: java.net.SocketTimeoutException) {
            throw SpeedTestError.ServerUnreachable("Test server timed out", e)
        } catch (e: java.io.IOException) {
            throw SpeedTestError.ServerUnreachable("Network I/O error: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Speed test failed", e)
            throw SpeedTestError.Unknown(e.message ?: "Speed test failed", e)
        }
    }

    private fun measurePingMs(): Int {
        val samples = ArrayList<Long>(pingSamples)
        repeat(pingSamples) {
            val nanos = measureNanoTime {
                val conn = open(PING_URL, "GET")
                try {
                    conn.connect()
                    // Drain tiny body so connection is complete.
                    conn.inputStream.use { stream ->
                        val buf = ByteArray(256)
                        while (stream.read(buf) != -1) {
                            // discard
                        }
                    }
                    if (conn.responseCode !in 200..399) {
                        throw SpeedTestError.ServerUnreachable(
                            "Ping HTTP ${conn.responseCode}",
                        )
                    }
                } finally {
                    conn.disconnect()
                }
            }
            samples.add(nanos / 1_000_000L)
        }
        return samples.average().roundToInt().coerceAtLeast(0)
    }

    private fun measureDownloadMbps(): Float {
        val url = "$DOWNLOAD_URL?bytes=$downloadBytes"
        val conn = open(url, "GET")
        try {
            conn.connect()
            if (conn.responseCode !in 200..399) {
                throw SpeedTestError.ServerUnreachable("Download HTTP ${conn.responseCode}")
            }
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            val nanos = measureNanoTime {
                BufferedInputStream(conn.inputStream).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                    }
                }
            }
            if (total <= 0L) {
                throw SpeedTestError.ServerUnreachable("Download returned 0 bytes")
            }
            return bytesPerSecToMbps(total, nanos)
        } finally {
            conn.disconnect()
        }
    }

    private fun measureUploadMbps(): Float {
        val payload = ByteArray(uploadBytes) { (it % 251).toByte() }
        val conn = open(UPLOAD_URL, "POST")
        try {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(payload.size)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            val nanos = measureNanoTime {
                BufferedOutputStream(conn.outputStream).use { out ->
                    var offset = 0
                    while (offset < payload.size) {
                        val chunk = minOf(64 * 1024, payload.size - offset)
                        out.write(payload, offset, chunk)
                        offset += chunk
                    }
                    out.flush()
                }
                // Read response so the server fully processes the upload.
                conn.inputStream.use { stream ->
                    val buf = ByteArray(1024)
                    while (stream.read(buf) != -1) {
                        // discard
                    }
                }
            }
            if (conn.responseCode !in 200..399) {
                throw SpeedTestError.ServerUnreachable("Upload HTTP ${conn.responseCode}")
            }
            return bytesPerSecToMbps(payload.size.toLong(), nanos)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WifiAR-SpeedTest/1.0")
            setRequestProperty("Accept", "*/*")
        }
        return conn
    }

    private fun bytesPerSecToMbps(bytes: Long, nanos: Long): Float {
        val seconds = max(nanos / 1_000_000_000.0, 1e-6)
        val bits = bytes * 8.0
        return (bits / seconds / 1_000_000.0).toFloat()
    }

    companion object {
        private const val TAG = "ThroughputSpeedTest"

        // Cloudflare public speed-test endpoints (no API key).
        private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down"
        private const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
        private const val PING_URL = "https://speed.cloudflare.com/__down?bytes=0"
    }
}
