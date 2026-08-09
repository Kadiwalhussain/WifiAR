package com.wifiar.app.scanner

/**
 * Wi‑Fi frequency ↔ channel helpers for the Networks insights UI.
 */
object WifiChannelUtils {

    enum class Band { BAND_2_4, BAND_5, BAND_6, UNKNOWN }

    fun bandOf(frequencyMhz: Int): Band = when {
        frequencyMhz in 2400..2500 -> Band.BAND_2_4
        frequencyMhz in 4900..5900 -> Band.BAND_5
        frequencyMhz in 5925..7125 -> Band.BAND_6
        else -> Band.UNKNOWN
    }

    /** IEEE channel number for common 2.4 / 5 GHz centers. */
    fun channelOf(frequencyMhz: Int): Int {
        return when {
            frequencyMhz in 2412..2484 -> {
                if (frequencyMhz == 2484) 14 else (frequencyMhz - 2407) / 5
            }
            frequencyMhz in 5035..5980 -> {
                // 5 GHz: channel = (f - 5000) / 5 for most UNII centers
                (frequencyMhz - 5000) / 5
            }
            frequencyMhz in 5955..7115 -> {
                // 6 GHz approximate
                (frequencyMhz - 5950) / 5
            }
            else -> 0
        }
    }

    fun bandLabel(band: Band): String = when (band) {
        Band.BAND_2_4 -> "2.4 GHz"
        Band.BAND_5 -> "5 GHz"
        Band.BAND_6 -> "6 GHz"
        Band.UNKNOWN -> "—"
    }

    fun qualityLabel(rssiDbm: Int): String = when {
        rssiDbm >= -50 -> "Excellent"
        rssiDbm >= -60 -> "Good"
        rssiDbm >= -70 -> "Fair"
        rssiDbm >= -80 -> "Weak"
        else -> "Poor"
    }

    fun qualityColorArgb(rssiDbm: Int): Long = when {
        rssiDbm >= -60 -> 0xFF4CAF50
        rssiDbm >= -75 -> 0xFFFFEB3B
        rssiDbm >= -90 -> 0xFFAB47BC
        else -> 0xFFE53935
    }
}
