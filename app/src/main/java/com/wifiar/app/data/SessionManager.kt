package com.wifiar.app.data

import com.wifiar.app.data.local.MappingSessionDao
import com.wifiar.app.data.local.MappingSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * In-memory + Room-backed mapping session lifecycle.
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

    suspend fun startSession(locationName: String): MappingSessionEntity {
        _activeSession.value?.let { return it }

        val session = MappingSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            locationName = locationName.trim().ifBlank { "Untitled session" },
            startTimeMs = System.currentTimeMillis(),
            endTimeMs = null,
        )
        sessionDao.insert(session)
        _activeSession.value = session
        return session
    }

    /**
     * Re-open a previously ended session (Cloud Anchor resume / multi-day mapping).
     */
    suspend fun resumeSession(sessionId: String): MappingSessionEntity? {
        val existing = sessionDao.getById(sessionId) ?: return null
        sessionDao.reopenSession(sessionId)
        val resumed = existing.copy(endTimeMs = null)
        _activeSession.value = resumed
        return resumed
    }

    suspend fun endSession(): MappingSessionEntity? {
        val current = _activeSession.value ?: return null
        val endTime = System.currentTimeMillis()
        sessionDao.markEnded(current.sessionId, endTime)
        val closed = current.copy(endTimeMs = endTime)
        _activeSession.value = null
        return closed
    }

    suspend fun attachCloudAnchor(sessionId: String, cloudAnchorId: String) {
        sessionDao.setCloudAnchorId(sessionId, cloudAnchorId)
        val cur = _activeSession.value
        if (cur?.sessionId == sessionId) {
            _activeSession.value = cur.copy(cloudAnchorId = cloudAnchorId)
        }
    }
}
