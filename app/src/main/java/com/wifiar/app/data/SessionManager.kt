package com.wifiar.app.data

import com.wifiar.app.data.local.MappingSessionDao
import com.wifiar.app.data.local.MappingSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * In-memory + Room-backed mapping session lifecycle.
 *
 * Generates a UUID [sessionId] when the user starts a walkthrough and tracks
 * [locationName] / timestamps for history.
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
     * Start a new mapping session.
     *
     * @param locationName user label, e.g. "Home - Ground Floor"
     * @return the new session, or the existing one if already active
     */
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
     * End the current session (if any) and stamp [endTimeMs].
     */
    suspend fun endSession(): MappingSessionEntity? {
        val current = _activeSession.value ?: return null
        val endTime = System.currentTimeMillis()
        sessionDao.markEnded(current.sessionId, endTime)
        val closed = current.copy(endTimeMs = endTime)
        _activeSession.value = null
        return closed
    }
}
