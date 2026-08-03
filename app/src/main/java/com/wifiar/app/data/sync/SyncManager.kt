package com.wifiar.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wifiar.app.AppConfig
import com.wifiar.app.data.auth.TokenStore
import com.wifiar.app.data.local.MappingSessionDao
import com.wifiar.app.data.local.MappingSessionEntity
import com.wifiar.app.data.local.RssiSampleDao
import com.wifiar.app.data.local.SpeedTestDao
import com.wifiar.app.data.local.WifiArDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uploads finished Room sessions to the FastAPI backend in bulk batches.
 */
class SyncManager(
    context: Context,
    private val api: WifiArApi = ApiClient.api,
    private val tokenStore: TokenStore = TokenStore(context),
    private val sessionDao: MappingSessionDao = WifiArDatabase.getInstance(context).mappingSessionDao(),
    private val rssiDao: RssiSampleDao = WifiArDatabase.getInstance(context).rssiSampleDao(),
    private val speedDao: SpeedTestDao = WifiArDatabase.getInstance(context).speedTestDao(),
) {
    private val appContext = context.applicationContext

    /**
     * Enqueue a WorkManager job for [sessionId] when the network is available.
     */
    fun enqueueSessionSync(sessionId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SessionSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SessionSyncWorker.KEY_SESSION_ID to sessionId))
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            uniqueWorkName(sessionId),
            ExistingWorkPolicy.KEEP,
            request,
        )
        Log.d(TAG, "Enqueued sync for $sessionId")
    }

    /**
     * Enqueue sync for every ended-but-unsynced session.
     */
    fun enqueuePendingSessions() {
        // Fire-and-forget from app startup; worker loads pending list itself if id blank.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SessionSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SessionSyncWorker.KEY_SESSION_ID to SessionSyncWorker.ALL_PENDING))
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "wifiar-sync-pending",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Upload one local session (blocking / coroutine). Returns true on full success.
     */
    suspend fun syncSession(sessionId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenStore.accessToken
                ?: error("Not logged in — open Account to sign in before syncing")
            val auth = "Bearer $token"

            val session = sessionDao.getById(sessionId)
                ?: error("Session not found: $sessionId")
            if (session.synced && !session.remoteSessionId.isNullOrBlank()) {
                return@runCatching session.remoteSessionId!!
            }
            if (session.endTimeMs == null) {
                error("Session still active; end it before syncing")
            }

            val created = api.createSession(
                authorization = auth,
                body = SessionCreateRequest(
                    locationName = session.locationName,
                    clientSessionId = session.sessionId,
                    originMetadata = mapOf(
                        "startTimeMs" to session.startTimeMs.toString(),
                        "endTimeMs" to (session.endTimeMs?.toString() ?: ""),
                    ),

                    createdAtMs = session.startTimeMs,
                ),
            )
            val remoteId = created.id

            val samples = rssiDao.getAllForSessionOnce(sessionId)
            samples.chunked(AppConfig.SYNC_BATCH_SIZE).forEach { chunk ->
                api.uploadPoints(
                    authorization = auth,
                    sessionId = remoteId,
                    body = BulkRssiUpload(
                        points = chunk.map {
                            RssiPointIn(
                                poseX = it.poseX,
                                poseY = it.poseY,
                                poseZ = it.poseZ,
                                ssid = it.ssid,
                                bssid = it.bssid,
                                rssiDbm = it.rssiDbm,
                                recordedAtMs = it.timestampMs,
                            )
                        },
                    ),
                )
            }

            val tests = speedDao.getAllForSessionOnce(sessionId)
            if (tests.isNotEmpty()) {
                tests.chunked(AppConfig.SYNC_BATCH_SIZE).forEach { chunk ->
                    api.uploadSpeedTests(
                        authorization = auth,
                        sessionId = remoteId,
                        body = BulkSpeedTestUpload(
                            points = chunk.map {
                                SpeedTestPointIn(
                                    poseX = it.poseX,
                                    poseY = it.poseY,
                                    poseZ = it.poseZ,
                                    downloadMbps = it.downloadMbps,
                                    uploadMbps = it.uploadMbps,
                                    pingMs = it.pingMs,
                                    recordedAtMs = it.timestampMs,
                                )
                            },
                        ),
                    )
                }
            }

            sessionDao.markSynced(sessionId, remoteId, synced = true)
            Log.i(TAG, "Synced session $sessionId → remote $remoteId (${samples.size} points)")
            remoteId
        }
    }

    suspend fun syncAllPending(): Int = withContext(Dispatchers.IO) {
        val pending = sessionDao.getPendingSyncSessions()
        var ok = 0
        for (s in pending) {
            val result = syncSession(s.sessionId)
            if (result.isSuccess) ok++
            else Log.w(TAG, "Sync failed for ${s.sessionId}: ${result.exceptionOrNull()?.message}")
        }
        ok
    }

    companion object {
        private const val TAG = "SyncManager"
        const val WORK_TAG = "wifiar-session-sync"

        fun uniqueWorkName(sessionId: String) = "wifiar-sync-$sessionId"
    }
}
