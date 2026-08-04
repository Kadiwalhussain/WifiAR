package com.wifiar.app.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.wifiar.app.ar.HeatmapMeshBuilder
import com.wifiar.app.data.UserPreferences
import com.wifiar.app.data.interpolation.IdwInterpolator
import com.wifiar.app.data.local.RssiSampleEntity
import com.wifiar.app.data.local.SpeedTestEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

/**
 * Exports session heatmaps (PNG) and raw data (CSV) for ShareSheet (Part 10).
 */
class HeatmapExporter(
    private val context: Context,
) {
    private val appContext = context.applicationContext

    data class ExportFiles(
        val pngUri: Uri?,
        val csvUri: Uri?,
    )

    /**
     * Build shareable PNG (top-down heatmap + legend) and CSV of samples + speed tests.
     */
    suspend fun exportSession(
        locationName: String,
        startTimeMs: Long,
        samples: List<RssiSampleEntity>,
        speedTests: List<SpeedTestEntity>,
    ): ExportFiles = withContext(Dispatchers.Default) {
        val cell = UserPreferences.gridCellSizeM
        val idw = IdwInterpolator(cellSize = cell)
        val grid = idw.interpolate(samples)
        val mesh = HeatmapMeshBuilder(
            alpha = 1.0f,
            deadZoneThresholdDbm = UserPreferences.rssiDeadDbm.toFloat(),
        )
        val plane = mesh.build(grid, samples)
        val heat = plane?.bitmap

        val png = if (heat != null && !heat.isRecycled) {
            composeReportBitmap(heat, locationName, startTimeMs, samples.size)
        } else {
            null
        }

        val pngUri = png?.let { writePng(it, "wifiar_heatmap_${System.currentTimeMillis()}.png") }
        val csvUri = writeCsv(locationName, samples, speedTests)
        ExportFiles(pngUri = pngUri, csvUri = csvUri)
    }

    fun shareImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "WifiAR heatmap")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(
            Intent.createChooser(intent, "Share heatmap").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun shareCsv(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "WifiAR session data")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(
            Intent.createChooser(intent, "Share CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun shareBoth(png: Uri?, csv: Uri?) {
        val uris = ArrayList<Uri>()
        if (png != null) uris.add(png)
        if (csv != null) uris.add(csv)
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            if (png != null) shareImage(png) else shareCsv(csv!!)
            return
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(
            Intent.createChooser(intent, "Share WifiAR export")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun composeReportBitmap(
        heat: Bitmap,
        locationName: String,
        startTimeMs: Long,
        sampleCount: Int,
    ): Bitmap {
        val legendW = 160
        val headerH = 100
        val pad = 24
        val heatW = heat.width.coerceAtLeast(256)
        val heatH = heat.height.coerceAtLeast(256)
        val scale = maxOf(1f, 512f / maxOf(heatW, heatH))
        val drawW = (heatW * scale).toInt()
        val drawH = (heatH * scale).toInt()
        val totalW = pad * 2 + drawW + legendW + pad
        val totalH = pad * 2 + headerH + drawH + pad

        val out = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D47A1")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 22f
        }
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(startTimeMs))

        canvas.drawText("WifiAR Heatmap", pad.toFloat(), pad + 36f, titlePaint)
        canvas.drawText(locationName.ifBlank { "Untitled" }, pad.toFloat(), pad + 68f, bodyPaint)
        canvas.drawText("$date · $sampleCount samples", pad.toFloat(), pad + 94f, bodyPaint)

        val scaled = Bitmap.createScaledBitmap(heat, drawW, drawH, true)
        canvas.drawBitmap(scaled, pad.toFloat(), (pad + headerH).toFloat(), null)
        if (scaled !== heat) scaled.recycle()

        // Legend
        val lx = pad + drawW + 24f
        var ly = (pad + headerH + 20).toFloat()
        val legendTitle = Paint(bodyPaint).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 20f
            color = Color.BLACK
        }
        canvas.drawText("RSSI (dBm)", lx, ly, legendTitle)
        ly += 28f
        val strong = UserPreferences.rssiStrongDbm
        val medium = UserPreferences.rssiMediumDbm
        val dead = UserPreferences.rssiDeadDbm
        drawLegendSwatch(canvas, lx, ly, Color.parseColor("#2E7D32"), "≥ $strong (strong)")
        ly += 36f
        drawLegendSwatch(canvas, lx, ly, Color.parseColor("#F9A825"), "$strong…$medium")
        ly += 36f
        drawLegendSwatch(canvas, lx, ly, Color.parseColor("#6A1B9A"), "< $medium (weak)")
        ly += 36f
        drawLegendSwatch(canvas, lx, ly, Color.parseColor("#8B0000"), "≤ $dead (dead)")
        ly += 48f
        val notePaint = Paint(bodyPaint).apply { textSize = 16f; color = Color.GRAY }
        canvas.drawText("Top-down (x,z)", lx, ly, notePaint)
        ly += 22f
        canvas.drawText("IDW · cell ${UserPreferences.gridCellSizeM}m", lx, ly, notePaint)

        return out
    }

    private fun drawLegendSwatch(canvas: Canvas, x: Float, y: Float, color: Int, label: String) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRect(x, y - 18f, x + 28f, y + 4f, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.DKGRAY
            textSize = 18f
        }
        canvas.drawText(label, x + 36f, y, t)
    }

    private fun writePng(bitmap: Bitmap, name: String): Uri? {
        return try {
            val dir = File(appContext.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, name)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCsv(
        locationName: String,
        samples: List<RssiSampleEntity>,
        speedTests: List<SpeedTestEntity>,
    ): Uri? {
        return try {
            val dir = File(appContext.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "wifiar_session_${System.currentTimeMillis()}.csv")
            file.bufferedWriter().use { w ->
                w.appendLine("# WifiAR export · $locationName")
                w.appendLine("# section,rssi_samples")
                w.appendLine("id,sessionId,timestampMs,poseX,poseY,poseZ,ssid,bssid,rssiDbm,frequencyMhz")
                for (s in samples) {
                    w.appendLine(
                        listOf(
                            s.id,
                            s.sessionId,
                            s.timestampMs,
                            s.poseX,
                            s.poseY,
                            s.poseZ,
                            escapeCsv(s.ssid),
                            s.bssid,
                            s.rssiDbm,
                            s.frequencyMhz,
                        ).joinToString(","),
                    )
                }
                w.appendLine()
                w.appendLine("# section,speed_tests")
                w.appendLine(
                    "id,sessionId,timestampMs,poseX,poseY,poseZ,downloadMbps,uploadMbps,pingMs,backend",
                )
                for (t in speedTests) {
                    w.appendLine(
                        listOf(
                            t.id,
                            t.sessionId,
                            t.timestampMs,
                            t.poseX,
                            t.poseY,
                            t.poseZ,
                            t.downloadMbps,
                            t.uploadMbps,
                            t.pingMs,
                            t.backend,
                        ).joinToString(","),
                    )
                }
            }
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun escapeCsv(s: String): String {
        val needs = s.contains(',') || s.contains('"') || s.contains('\n')
        return if (needs) "\"${s.replace("\"", "\"\"")}\"" else s
    }
}
