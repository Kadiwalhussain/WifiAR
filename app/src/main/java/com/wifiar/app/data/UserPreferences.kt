package com.wifiar.app.data

import android.content.Context
import android.content.SharedPreferences
import com.wifiar.app.AppConfig

/**
 * User-tunable settings (Part 10). Initialized from [com.wifiar.app.WifiArApp].
 */
object UserPreferences {

    private const val PREFS = "wifiar_user_prefs"
    private const val KEY_ONBOARDING = "onboarding_done"
    private const val KEY_STRONG = "rssi_strong"
    private const val KEY_MEDIUM = "rssi_medium"
    private const val KEY_DEAD = "rssi_dead"
    private const val KEY_PATH_LOSS_INDOOR = "path_loss_indoor"
    private const val KEY_GRID_CELL = "grid_cell_m"
    private const val KEY_CLOUD_API_CONFIGURED = "cloud_api_ack"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    private fun p(): SharedPreferences =
        prefs ?: error("UserPreferences.init() not called")

    var onboardingDone: Boolean
        get() = p().getBoolean(KEY_ONBOARDING, false)
        set(v) = p().edit().putBoolean(KEY_ONBOARDING, v).apply()

    /** Green threshold (dBm) — RSSI ≥ this is strong. Default −50. */
    var rssiStrongDbm: Int
        get() = p().getInt(KEY_STRONG, -50)
        set(v) = p().edit().putInt(KEY_STRONG, v).apply()

    /** Yellow threshold (dBm) — between strong and this is medium. Default −70. */
    var rssiMediumDbm: Int
        get() = p().getInt(KEY_MEDIUM, AppConfig.COVERAGE_THRESHOLD_DBM)
        set(v) = p().edit().putInt(KEY_MEDIUM, v).apply()

    /** Dead-zone threshold (dBm). Default −80. */
    var rssiDeadDbm: Int
        get() = p().getInt(KEY_DEAD, AppConfig.DEAD_ZONE_THRESHOLD_DBM)
        set(v) = p().edit().putInt(KEY_DEAD, v).apply()

    /** True = indoor n=3.0, false = open n=2.0. */
    var pathLossIndoor: Boolean
        get() = p().getBoolean(KEY_PATH_LOSS_INDOOR, true)
        set(v) = p().edit().putBoolean(KEY_PATH_LOSS_INDOOR, v).apply()

    val pathLossExponent: Float
        get() = if (pathLossIndoor) {
            AppConfig.PATH_LOSS_EXPONENT_INDOOR
        } else {
            AppConfig.PATH_LOSS_EXPONENT_OPEN
        }

    /** IDW / heatmap grid cell size in metres. */
    var gridCellSizeM: Float
        get() = p().getFloat(KEY_GRID_CELL, 0.3f)
        set(v) = p().edit().putFloat(KEY_GRID_CELL, v.coerceIn(0.15f, 1.0f)).apply()

    /** User acknowledged Cloud Anchors API key setup. */
    var cloudApiAcknowledged: Boolean
        get() = p().getBoolean(KEY_CLOUD_API_CONFIGURED, false)
        set(v) = p().edit().putBoolean(KEY_CLOUD_API_CONFIGURED, v).apply()
}
