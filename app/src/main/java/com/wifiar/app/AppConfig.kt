package com.wifiar.app

/**
 * Central tunable constants for WifiAR.
 *
 * Keep runtime feature thresholds here so analysis / UI / rendering stay in sync.
 */
object AppConfig {

    /**
     * Interpolated RSSI at or below this value is treated as a **dead zone**
     * (practically unusable coverage). Default −80 dBm.
     */
    const val DEAD_ZONE_THRESHOLD_DBM: Int = -80

    /** Minimum connected cells for a region to be reported (filters single-cell noise). */
    const val DEAD_ZONE_MIN_CELLS: Int = 2

    /** Marker sphere radius in metres for dead-zone centroids in AR. */
    const val DEAD_ZONE_MARKER_RADIUS_M: Float = 0.12f

    /** How high above the floor plane to float dead-zone labels (metres). */
    const val DEAD_ZONE_LABEL_HEIGHT_M: Float = 0.45f

    // ── Speed test (Part 6) ──────────────────────────────────────────────────

    /**
     * Which speed-test backend to use.
     *
     * - [SpeedTestBackend.THROUGHPUT] — self-hosted / public HTTP download+upload
     *   (default; no external account; good for demos and college projects).
     * - [SpeedTestBackend.OOKLA] — Ookla Speedtest SDK path. Requires a partner
     *   developer account + SDK AAR and [OOKLA_API_KEY]; without them the manager
     *   returns a clear error instead of crashing.
     *
     * Flip this flag for demos — both code paths are wired.
     */
    val SPEED_TEST_BACKEND: SpeedTestBackend = SpeedTestBackend.THROUGHPUT

    /**
     * Ookla Speedtest partner API key (leave blank until you have a partner
     * account). Never commit a real production key; load from local.properties
     * / CI secrets in a real deployment.
     */
    const val OOKLA_API_KEY: String = ""

    /** Download payload size for the HTTP throughput backend (bytes). */
    const val SPEED_TEST_DOWNLOAD_BYTES: Int = 5_000_000 // ~5 MB

    /** Upload payload size for the HTTP throughput backend (bytes). */
    const val SPEED_TEST_UPLOAD_BYTES: Int = 2_000_000 // ~2 MB

    /** Number of HTTP RTT samples averaged for ping. */
    const val SPEED_TEST_PING_SAMPLES: Int = 4

    /** Per-phase socket/read timeout (ms). */
    const val SPEED_TEST_TIMEOUT_MS: Int = 20_000

    /** AR marker size for speed-test checkpoints. */
    const val SPEED_TEST_MARKER_RADIUS_M: Float = 0.10f

    const val SPEED_TEST_LABEL_HEIGHT_M: Float = 0.50f

    // ── Backend sync (Part 7) ────────────────────────────────────────────────

    /**
     * Base URL for the WifiAR FastAPI backend.
     * Emulator → host machine: http://10.0.2.2:8000/
     * Physical device on same LAN: use your computer's LAN IP.
     */
    const val API_BASE_URL: String = "http://10.0.2.2:8000/"

    /** Bulk upload chunk size for RSSI points. */
    const val SYNC_BATCH_SIZE: Int = 200

    // ── Multi-network comparison (Part 8) ────────────────────────────────────

    /**
     * RSSI at or above this is "usable coverage" (yellow tier and better).
     * Used for per-network coverage % on the comparison screen.
     */
    const val COVERAGE_THRESHOLD_DBM: Int = -70

    /** Minimum samples required for a network to appear in comparison. */
    const val NETWORK_COMPARE_MIN_SAMPLES: Int = 3
}



/**
 * Selectable speed-test implementation (demo switch).
 */
enum class SpeedTestBackend {
    /** Option B — lightweight HTTP download/upload against a public endpoint. */
    THROUGHPUT,

    /**
     * Option A — Ookla Speedtest SDK.
     * Proprietary partner SDK; not on public Maven without a license.
     */
    OOKLA,
}
