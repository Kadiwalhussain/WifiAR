package com.wifiar.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background upload of one session (or all pending) when network is available.
 */
class SessionSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID).orEmpty()
        val syncManager = SyncManager(applicationContext)
        return try {
            if (sessionId == ALL_PENDING || sessionId.isBlank()) {
                val n = syncManager.syncAllPending()
                Log.i(TAG, "Pending sync finished: $n session(s)")
            } else {
                val outcome = syncManager.syncSession(sessionId)
                if (outcome.isFailure) {
                    Log.w(TAG, "Sync failed: ${outcome.exceptionOrNull()?.message}")
                    return Result.retry()
                }
            }
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Worker error", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SessionSyncWorker"
        const val KEY_SESSION_ID = "session_id"
        const val ALL_PENDING = "__all_pending__"
    }
}
