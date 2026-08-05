package com.wifiar.app.util

import java.util.Locale
import kotlin.math.abs

/**
 * Human-readable throughput formatting (bps → kbps → Mbps → Gbps).
 */
object SpeedFormat {

    /**
     * Format a value stored as megabits per second into the best unit.
     * Examples: `12.45 Mbps`, `850 kbps`, `1.20 Gbps`.
     */
    fun formatMbps(mbps: Float): String {
        if (!mbps.isFinite() || mbps < 0f) return "—"
        val abs = abs(mbps.toDouble())
        return when {
            abs >= 1000.0 -> String.format(Locale.US, "%.2f Gbps", mbps / 1000.0)
            abs >= 1.0 -> String.format(Locale.US, "%.2f Mbps", mbps)
            abs >= 0.001 -> String.format(Locale.US, "%.0f kbps", mbps * 1000.0)
            abs > 0.0 -> String.format(Locale.US, "%.0f bps", mbps * 1_000_000.0)
            else -> "0 kbps"
        }
    }

    /** Compact AR label: `↓12.4M ↑3.1M` or `↓850k ↑120k`. */
    fun formatPairCompact(downMbps: Float, upMbps: Float): String {
        return "↓${compact(downMbps)} ↑${compact(upMbps)}"
    }

    fun formatDetailLine(downloadMbps: Float, uploadMbps: Float, pingMs: Int): String {
        return "↓ ${formatMbps(downloadMbps)}  ·  ↑ ${formatMbps(uploadMbps)}  ·  ${pingMs} ms"
    }

    private fun compact(mbps: Float): String {
        if (!mbps.isFinite() || mbps < 0f) return "—"
        val abs = abs(mbps.toDouble())
        return when {
            abs >= 1000.0 -> String.format(Locale.US, "%.1fG", mbps / 1000.0)
            abs >= 1.0 -> String.format(Locale.US, "%.1fM", mbps)
            abs >= 0.001 -> String.format(Locale.US, "%.0fk", mbps * 1000.0)
            else -> String.format(Locale.US, "%.0fb", mbps * 1_000_000.0)
        }
    }
}
