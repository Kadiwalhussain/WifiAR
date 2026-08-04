package com.wifiar.app.ar

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.wifiar.app.data.local.MappingSessionDao
import com.wifiar.app.data.local.WifiArDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Hosts / resolves ARCore Cloud Anchors so multi-day sessions can share a
 * physical origin (Part 10).
 *
 * ## Setup (required for real hosting)
 * 1. Create a Google Cloud project and enable the **ARCore API**.
 * 2. Create an API key restricted to your app package + SHA-1.
 * 3. Add to AndroidManifest inside `<application>`:
 *    ```xml
 *    <meta-data
 *        android:name="com.google.android.ar.API_KEY"
 *        android:value="YOUR_KEY"/>
 *    ```
 * 4. Disclose to users that feature points are uploaded to Google
 *    (https://developers.google.com/ar/data-privacy).
 *
 * If the API key is missing or hosting fails, callers should **gracefully
 * fall back** to a fresh local origin — never crash.
 */
class CloudAnchorManager(
    context: Context,
    private val sessionDao: MappingSessionDao =
        WifiArDatabase.getInstance(context).mappingSessionDao(),
) {
    private val appContext = context.applicationContext

    sealed class HostResult {
        data class Success(val cloudAnchorId: String) : HostResult()
        data class Failure(val reason: String) : HostResult()
    }

    sealed class ResolveResult {
        data class Success(val anchor: Anchor) : ResolveResult()
        data class Failure(val reason: String) : ResolveResult()
    }

    /**
     * True when `com.google.android.ar.API_KEY` meta-data is present and non-empty.
     */
    fun isApiKeyConfigured(): Boolean {
        return runCatching {
            val ai = appContext.packageManager.getApplicationInfo(
                appContext.packageName,
                PackageManager.GET_META_DATA,
            )
            val key = ai.metaData?.getString(META_API_KEY)
            !key.isNullOrBlank() && key != "YOUR_ARCORE_API_KEY"
        }.getOrDefault(false)
    }

    /**
     * Enable Cloud Anchor mode on an ARCore [Config]. Call before session.configure.
     */
    fun applyCloudConfig(session: Session, config: Config) {
        if (!isApiKeyConfigured()) return
        runCatching {
            config.cloudAnchorMode = Config.CloudAnchorMode.ENABLED
            session.configure(config)
            Log.d(TAG, "CloudAnchorMode ENABLED")
        }.onFailure {
            Log.w(TAG, "Could not enable Cloud Anchors: ${it.message}")
        }
    }

    /**
     * Host [localAnchor] to the cloud. Returns cloud ID or failure reason.
     * TTL default 30 days for multi-session continuity.
     */
    suspend fun host(
        session: Session,
        localAnchor: Anchor,
        ttlDays: Int = 30,
    ): HostResult {
        if (!isApiKeyConfigured()) {
            return HostResult.Failure(
                "ARCore API key not configured — see README (Cloud Anchors setup)",
            )
        }
        return try {
            suspendCancellableCoroutine { cont ->
                val future = session.hostCloudAnchorAsync(localAnchor, ttlDays) { id, state ->
                    if (!cont.isActive) return@hostCloudAnchorAsync
                    when {
                        state.isError || id.isNullOrBlank() -> {
                            cont.resume(
                                HostResult.Failure("Host failed: $state"),
                            )
                        }
                        else -> cont.resume(HostResult.Success(id))
                    }
                }
                cont.invokeOnCancellation {
                    runCatching { future.cancel() }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "host error", t)
            HostResult.Failure(t.message ?: "Host error")
        }
    }

    /**
     * Resolve a previously hosted cloud anchor ID.
     */
    suspend fun resolve(session: Session, cloudAnchorId: String): ResolveResult {
        if (!isApiKeyConfigured()) {
            return ResolveResult.Failure(
                "ARCore API key not configured — starting a new local session",
            )
        }
        if (cloudAnchorId.isBlank()) {
            return ResolveResult.Failure("Empty cloud anchor id")
        }
        return try {
            suspendCancellableCoroutine { cont ->
                val future = session.resolveCloudAnchorAsync(cloudAnchorId) { anchor, state ->
                    if (!cont.isActive) return@resolveCloudAnchorAsync
                    when {
                        state.isError || anchor == null -> {
                            cont.resume(ResolveResult.Failure("Resolve failed: $state"))
                        }
                        else -> cont.resume(ResolveResult.Success(anchor))
                    }
                }
                cont.invokeOnCancellation {
                    runCatching { future.cancel() }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "resolve error", t)
            ResolveResult.Failure(t.message ?: "Resolve error")
        }
    }

    /**
     * Create a temporary local anchor at the camera pose if tracking is good.
     */
    /**
     * Create a temporary local anchor at [worldPose] (ARCore Pose meters).
     * Prefer this over calling [Session.update] from UI threads — SceneView owns the frame loop.
     */
    fun createLocalAnchor(
        session: Session,
        worldPose: com.google.ar.core.Pose,
    ): Anchor? {
        return runCatching {
            session.createAnchor(worldPose)
        }.getOrNull()
    }

    /**
     * Best-effort camera anchor. Safe to call only when ARCore allows an extra update;
     * returns null on failure instead of crashing.
     */
    fun createLocalAnchorAtCamera(session: Session): Anchor? {
        return runCatching {
            val frame = session.update()
            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) return null
            session.createAnchor(camera.pose)
        }.getOrNull()
    }

    suspend fun saveCloudAnchorId(sessionId: String, cloudAnchorId: String) {
        sessionDao.setCloudAnchorId(sessionId, cloudAnchorId)
    }

    /**
     * Past sessions the user may resume.
     * - Non-blank [locationName]: ended sessions with matching name (case-insensitive).
     * - Blank name: only sessions that already have a Cloud Anchor ID (avoid
     *   prompting for every past session when the user starts something new).
     * Cloud-anchored rows are listed first so resolve is attempted when available.
     */
    suspend fun findResumableSessions(
        locationName: String = "",
    ): List<com.wifiar.app.data.local.MappingSessionEntity> {
        val name = locationName.trim()
        val raw = if (name.isBlank()) {
            sessionDao.getSessionsWithCloudAnchors()
        } else {
            sessionDao.getResumableSessions(name)
        }
        return raw.sortedByDescending { !it.cloudAnchorId.isNullOrBlank() }
    }

    companion object {
        private const val TAG = "CloudAnchorManager"
        const val META_API_KEY = "com.google.android.ar.API_KEY"
    }
}
