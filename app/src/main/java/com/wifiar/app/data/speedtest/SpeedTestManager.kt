package com.wifiar.app.data.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.wifiar.app.AppConfig
import com.wifiar.app.SpeedTestBackend
import com.wifiar.app.data.local.SpeedTestDao
import com.wifiar.app.data.local.SpeedTestEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Orchestrates speed tests and optional Room persistence.
 *
 * Backend is selected via [AppConfig.SPEED_TEST_BACKEND] so demos can flip
 * between Ookla (Option A) and HTTP throughput (Option B).
 */
class SpeedTestManager(
    context: Context,
    private val speedTestDao: SpeedTestDao,
    backend: SpeedTestBackend = AppConfig.SPEED_TEST_BACKEND,
) {
    private val appContext = context.applicationContext
    private val engine: SpeedTestEngine = when (backend) {
        SpeedTestBackend.THROUGHPUT -> ThroughputSpeedTestEngine()
        SpeedTestBackend.OOKLA -> OoklaSpeedTestEngine()
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastOutcome = MutableStateFlow<SpeedTestOutcome?>(null)
    val lastOutcome: StateFlow<SpeedTestOutcome?> = _lastOutcome.asStateFlow()

    val backendName: String get() = engine.name

    /**
     * Run a speed test. Never throws to the caller — errors become
     * [SpeedTestOutcome.Failure].
     */
    suspend fun runSpeedTest(): SpeedTestOutcome {
        if (_isRunning.value) {
            return SpeedTestOutcome.Failure(
                SpeedTestError.Unknown("A speed test is already running"),
            )
        }
        _isRunning.value = true
        return try {
            if (!hasInternet()) {
                SpeedTestOutcome.Failure(SpeedTestError.NoInternet()).also {
                    _lastOutcome.value = it
                }
            } else {
                val result = withContext(Dispatchers.IO) {
                    engine.run()
                }
                SpeedTestOutcome.Success(result).also { _lastOutcome.value = it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpeedTestError) {
            SpeedTestOutcome.Failure(e).also { _lastOutcome.value = it }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected speed test error", e)
            SpeedTestOutcome.Failure(
                SpeedTestError.Unknown(e.message ?: "Unexpected error", e),
            ).also { _lastOutcome.value = it }
        } finally {
            _isRunning.value = false
        }
    }

    /**
     * Persist a successful result at the given AR pose for [sessionId].
     */
    suspend fun saveResult(
        sessionId: String,
        poseX: Float,
        poseY: Float,
        poseZ: Float,
        result: SpeedTestResult,
    ): SpeedTestEntity {
        val entity = SpeedTestEntity(
            sessionId = sessionId,
            poseX = poseX,
            poseY = poseY,
            poseZ = poseZ,
            downloadMbps = result.downloadMbps,
            uploadMbps = result.uploadMbps,
            pingMs = result.pingMs,
            timestampMs = result.timestampMs,
            backend = result.backend,
        )
        val id = speedTestDao.insert(entity)
        return entity.copy(id = id)
    }

    fun observeForSession(sessionId: String): Flow<List<SpeedTestEntity>> =
        speedTestDao.getAllForSession(sessionId)

    suspend fun getAllForSessionOnce(sessionId: String): List<SpeedTestEntity> =
        speedTestDao.getAllForSessionOnce(sessionId)

    fun clearLastOutcome() {
        _lastOutcome.value = null
    }

    private fun hasInternet(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "SpeedTestManager"
    }
}
