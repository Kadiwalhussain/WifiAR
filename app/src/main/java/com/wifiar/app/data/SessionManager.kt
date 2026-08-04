package com.wifiar.app.data

import android.util.Log
import com.wifiar.app.data.local.MappingSessionDao
import com.wifiar.app.data.local.MappingSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * In-memory + Room-backed mapping session lifecycle.
 * All public methods are crash-safe: DB failures log and return null / false.
 */
class SessionManager(
    private val sessionDao: MappingSessionDao,
) {
    private val _activeSession = MutableStateFlow<MappingSessionEntity?>(null)
    val activeSession: StateFlow<MappingSessionEntity?> = _activeSession.asStateFlow()

    val isSessionActive: Boolean
        get() = _activeSession.value != null

    val currentSessionId: String?
        get() = _activeSession.value?.sessionId

    /**
     * Start a new mapping session. If one is already active it is ended first
     * so we never stack two live fusions.
     */
    suspend fun startSession(locationName: String): MappingSessionEntity? {
        return try {
            if (_activeSession.value != null) {
                endSession()
            }
            val session = MappingSessionEntity(
                sessionId = UUID.randomUUID().toString(),
                locationName = locationName.trim().ifBlank { "Untitled session" },
                startTimeMs = System.currentTimeMillis(),
                endTimeMs = null,
            )
            sessionDao.insert(session)
            _activeSession.value = session
            session
        } catch (t: Throwable) {
            Log.e(TAG, "startSession failed", t)
            null
        }
    }

    /**
     * Re-open a previously ended session (Cloud Anchor resume / multi-day mapping).
     * Ends any currently active session first.
     */
    suspend fun resumeSession(sessionId: String): MappingSessionEntity? {
        return try {
            if (_activeSession.value?.sessionId == sessionId) {
                return _activeSession.value
            }
            if (_activeSession.value != null) {
                endSession()
            }
            val existing = sessionDao.getById(sessionId) ?: return null
            sessionDao.reopenSession(sessionId)
            val resumed = existing.copy(endTimeMs = null)
            _activeSession.value = resumed
            resumed
        } catch (t: Throwable) {
            Log.e(TAG, "resumeSession failed", t)
            null
        }
    }

    suspend fun endSession(): MappingSessionEntity? {
        return try {
            val current = _activeSession.value ?: return null
            val endTime = System.currentTimeMillis()
            sessionDao.markEnded(current.sessionId, endTime)
            val closed = current.copy(endTimeMs = endTime)
            _activeSession.value = null
            closed
        } catch (t: Throwable) {
            Log.e(TAG, "endSession failed", t)
            // Clear local active state even if Room write fails so UI unblocks.
            val orphan = _activeSession.value
            _activeSession.value = null
            orphan
        }
    }

    suspend fun attachCloudAnchor(sessionId: String, cloudAnchorId: String) {
        try {
            sessionDao.setCloudAnchorId(sessionId, cloudAnchorId)
            val cur = _activeSession.value
            if (cur?.sessionId == sessionId) {
                _activeSession.value = cur.copy(cloudAnchorId = cloudAnchorId)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "attachCloudAnchor failed", t)
        }
    }

    companion object {
        private const val TAG = "SessionManager"
    }
}
