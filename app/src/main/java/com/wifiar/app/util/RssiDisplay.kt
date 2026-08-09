package com.wifiar.app.util

import com.wifiar.app.data.UserPreferences
import kotlin.math.roundToInt

/**
 * Format RSSI for UI: dBm or relative % (Settings → Signal Units).
 *
 * Percent maps roughly −100 dBm → 0% and −30 dBm → 100% (clamped).
 */
object RssiDisplay {

    fun format(rssiDbm: Int, units: String = UserPreferences.signalUnits): String {
        return when (units) {
            "percent" -> "${toPercent(rssiDbm)}%"
            else -> "$rssiDbm dBm"
        }
    }

    fun toPercent(rssiDbm: Int): Int {
        // Linear map: -100 → 0, -30 → 100
        val p = ((rssiDbm + 100f) / 70f) * 100f
        return p.roundToInt().coerceIn(0, 100)
    }
}
