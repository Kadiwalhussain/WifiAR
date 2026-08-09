package com.wifiar.app.data.analysis

import com.wifiar.app.data.local.RssiSampleEntity

/**
 * Picks samples for **coverage** heatmaps / dead zones so IDW is not polluted
 * by mixing every visible AP at the same pose.
 *
 * Preference order:
 * 1. Connected BSSID if present in the session
 * 2. Connected SSID match (case-insensitive) if BSSID unknown
 * 3. Most frequent BSSID in the session
 */
object CoverageSampleSelector {

    fun selectForCoverage(
        samples: List<RssiSampleEntity>,
        preferredBssid: String? = null,
        preferredSsid: String? = null,
    ): List<RssiSampleEntity> {
        if (samples.isEmpty()) return emptyList()

        val bssidNorm = preferredBssid?.trim().orEmpty()
        val ssidNorm = preferredSsid?.trim().orEmpty()
        val invalidBssid = bssidNorm.isEmpty() ||
            bssidNorm == "02:00:00:00:00:00" ||
            bssidNorm.equals("null", ignoreCase = true)

        val byBssid = if (!invalidBssid) {
            samples.filter { it.bssid.equals(bssidNorm, ignoreCase = true) }
        } else {
            emptyList()
        }
        if (byBssid.isNotEmpty()) return byBssid

        val bySsid = if (ssidNorm.isNotEmpty() &&
            !ssidNorm.equals("Unknown network", ignoreCase = true) &&
            !ssidNorm.equals("Wi‑Fi", ignoreCase = true) &&
            !ssidNorm.equals("Wi-Fi", ignoreCase = true)
        ) {
            samples.filter { it.ssid.equals(ssidNorm, ignoreCase = true) }
        } else {
            emptyList()
        }
        if (bySsid.isNotEmpty()) {
            // Prefer single BSSID under that SSID (most samples).
            val topBssid = bySsid.groupingBy { it.bssid }.eachCount().maxByOrNull { it.value }?.key
            return if (topBssid != null) bySsid.filter { it.bssid == topBssid } else bySsid
        }

        val topBssid = samples
            .filter { it.bssid.isNotBlank() }
            .groupingBy { it.bssid }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: return samples
        return samples.filter { it.bssid == topBssid }
    }

    /**
     * Optional spatial smoothing: average RSSI in small floor cells for the
     * selected network (reduces scan flicker for AR visualization).
     */
    fun smoothSpatially(
        samples: List<RssiSampleEntity>,
        cellSizeM: Float = 0.4f,
    ): List<RssiSampleEntity> {
        if (samples.size < 2) return samples
        val cell = cellSizeM.coerceIn(0.15f, 2f)
        val groups = LinkedHashMap<Long, MutableList<RssiSampleEntity>>()
        for (s in samples) {
            if (!s.poseX.isFinite() || !s.poseZ.isFinite()) continue
            val cx = (s.poseX / cell).toInt()
            val cz = (s.poseZ / cell).toInt()
            val key = (cx.toLong() shl 32) xor (cz.toLong() and 0xffffffffL)
            groups.getOrPut(key) { ArrayList() }.add(s)
        }
        return groups.values.map { list ->
            val latest = list.maxBy { it.timestampMs }
            val avg = list.map { it.rssiDbm }.average().toInt()
            latest.copy(rssiDbm = avg)
        }.sortedBy { it.timestampMs }
    }
}
