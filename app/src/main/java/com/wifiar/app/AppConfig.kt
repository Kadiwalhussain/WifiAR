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

    /** Marker sphere radius in metres for dead-zone centroids in AR (kept small for clarity). */
    const val DEAD_ZONE_MARKER_RADIUS_M: Float = 0.055f

    /** How high above the floor plane to float dead-zone labels (metres). */
    const val DEAD_ZONE_LABEL_HEIGHT_M: Float = 0.28f

    /** Core sample sphere radius in AR (metres). */
    const val SAMPLE_SPHERE_RADIUS_M: Float = 0.05f

    /**
     * Max sample spheres in AR. Keep modest — Filament OOM/crashes if hundreds of
     * nodes + materials are created/destroyed every frame.
     */
    const val AR_MAX_SAMPLE_SPHERES: Int = 80

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
    const val SPEED_TEST_DOWNLOAD_BYTES: Int = 15_000_000 // ~15 MB — more stable full-rate measure

    /** Upload payload size for the HTTP throughput backend (bytes). */
    const val SPEED_TEST_UPLOAD_BYTES: Int = 5_000_000 // ~5 MB

    /** Number of HTTP RTT samples averaged for ping. */
    const val SPEED_TEST_PING_SAMPLES: Int = 4

    /** Per-phase socket/read timeout (ms) — larger for 15 MB download. */
    const val SPEED_TEST_TIMEOUT_MS: Int = 45_000

    /** AR marker size for speed-test checkpoints. */
    const val SPEED_TEST_MARKER_RADIUS_M: Float = 0.07f

    const val SPEED_TEST_LABEL_HEIGHT_M: Float = 0.32f

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

    // ── Router placement (Part 9) ────────────────────────────────────────────

    /**
     * Log-distance path-loss exponent (n).
     * ~2.0 open space, ~3.0 typical indoor with walls (default).
     * This is a simplified model — no wall/material sensing.
     */
    const val PATH_LOSS_EXPONENT_INDOOR: Float = 3.0f
    const val PATH_LOSS_EXPONENT_OPEN: Float = 2.0f

    /** Reference distance d0 (metres) for path-loss model. */
    const val PATH_LOSS_D0_M: Float = 1.0f

    /**
     * Free-space path loss at d0 for ~2.4 GHz (~40 dB at 1 m).
     * Used with typical AP TX power (~20 dBm) → ~−20 dBm RSSI at 1 m.
     */
    const val PATH_LOSS_PL_D0_DB: Float = 40.0f

    /** Assumed AP transmit power (dBm). */
    const val ROUTER_TX_POWER_DBM: Float = 20.0f

    /** Candidate router grid spacing (metres). */
    const val ROUTER_CANDIDATE_SPACING_M: Float = 0.5f

    /** Target evaluation grid spacing (metres). */
    const val ROUTER_TARGET_SPACING_M: Float = 0.4f

    /** Dead-zone penalty weight in placement score. */
    const val ROUTER_DEAD_ZONE_PENALTY: Float = 1.0f

    /** Minimum fused samples before recommending placement. */
    const val ROUTER_RECOMMEND_MIN_SAMPLES: Int = 15

    /** AR marker radius for recommended router spot. */
    const val ROUTER_MARKER_RADIUS_M: Float = 0.08f

    /** Max seconds to wait for Cloud Anchor host/resolve before falling back. */
    const val CLOUD_ANCHOR_TIMEOUT_SEC: Long = 12L
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
