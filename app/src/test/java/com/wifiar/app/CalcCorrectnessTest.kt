package com.wifiar.app

import com.wifiar.app.ar.Pose3D
import com.wifiar.app.data.analysis.CoverageSampleSelector
import com.wifiar.app.data.analysis.DeadZoneDetector
import com.wifiar.app.data.analysis.PathLossModel
import com.wifiar.app.data.interpolation.IdwInterpolator
import com.wifiar.app.data.local.RssiSampleEntity
import com.wifiar.app.scanner.WifiChannelUtils
import com.wifiar.app.util.RssiDisplay
import com.wifiar.app.util.SpeedFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure JVM checks for RF / interpolation math used by live mapping.
 */
class CalcCorrectnessTest {

    private fun sample(
        x: Float,
        z: Float,
        rssi: Int,
        ssid: String = "Home",
        bssid: String = "aa:bb:cc:dd:ee:01",
        id: Long = 0L,
    ) = RssiSampleEntity(
        id = id,
        sessionId = "s1",
        timestampMs = 1_000L + id,
        poseX = x,
        poseY = 1.4f,
        poseZ = z,
        ssid = ssid,
        bssid = bssid,
        rssiDbm = rssi,
        frequencyMhz = 2437,
    )

    @Test
    fun pathLoss_atD0_isPlD0() {
        val model = PathLossModel.indoor()
        val pl = model.pathLossDb(1.0f)
        assertEquals(AppConfig.PATH_LOSS_PL_D0_DB, pl, 0.01f)
    }

    @Test
    fun pathLoss_increasesWithDistance() {
        val model = PathLossModel.indoor()
        val near = model.predictedRssi(
            Pose3D(0f, 0f, 0f, 0L),
            AppConfig.ROUTER_TX_POWER_DBM,
            Pose3D(1f, 0f, 0f, 0L),
        )
        val far = model.predictedRssi(
            Pose3D(0f, 0f, 0f, 0L),
            AppConfig.ROUTER_TX_POWER_DBM,
            Pose3D(8f, 0f, 0f, 0L),
        )
        assertTrue("far should be weaker: near=$near far=$far", far < near)
    }

    @Test
    fun idw_atSample_returnsSampleRssi() {
        val idw = IdwInterpolator(cellSize = 0.5f)
        val samples = listOf(
            sample(0f, 0f, -40),
            sample(2f, 0f, -70),
        )
        val at = idw.estimateAtSamples(0f, 0f, samples)
        assertEquals(-40f, at, 0.5f)
    }

    @Test
    fun idw_betweenSamples_isBetween() {
        val idw = IdwInterpolator(cellSize = 0.5f)
        val samples = listOf(
            sample(0f, 0f, -40),
            sample(4f, 0f, -80),
        )
        val mid = idw.estimateAtSamples(2f, 0f, samples)
        assertTrue("mid=$mid", mid < -40f && mid > -80f)
    }

    @Test
    fun deadZone_detectsWeakCluster() {
        val idw = IdwInterpolator(cellSize = 0.4f, paddingMeters = 0.2f)
        // Strong at origin, weak cluster far away
        val samples = listOf(
            sample(0f, 0f, -45, id = 1),
            sample(0.3f, 0f, -48, id = 2),
            sample(5f, 5f, -92, id = 3),
            sample(5.3f, 5f, -95, id = 4),
            sample(5f, 5.3f, -93, id = 5),
            sample(5.3f, 5.3f, -94, id = 6),
        )
        val grid = idw.interpolate(samples)
        val zones = DeadZoneDetector(thresholdDbm = -80f, minCells = 2).detect(grid)
        assertTrue("expected dead zone, got ${zones.size}", zones.isNotEmpty())
        assertTrue(zones.first().worstRssiDbm <= -80f)
    }

    @Test
    fun coverageSelector_prefersConnectedBssid() {
        val samples = listOf(
            sample(0f, 0f, -50, bssid = "aa:01", id = 1),
            sample(0f, 0f, -80, bssid = "bb:02", id = 2),
            sample(1f, 0f, -55, bssid = "aa:01", id = 3),
            sample(1f, 0f, -90, bssid = "bb:02", id = 4),
        )
        val selected = CoverageSampleSelector.selectForCoverage(
            samples,
            preferredBssid = "bb:02",
        )
        assertEquals(2, selected.size)
        assertTrue(selected.all { it.bssid == "bb:02" })
    }

    @Test
    fun coverageSelector_doesNotMixNetworks() {
        val samples = listOf(
            sample(0f, 0f, -40, ssid = "A", bssid = "aa", id = 1),
            sample(0f, 0f, -90, ssid = "B", bssid = "bb", id = 2),
            sample(1f, 0f, -42, ssid = "A", bssid = "aa", id = 3),
            sample(1f, 0f, -88, ssid = "B", bssid = "bb", id = 4),
        )
        val selected = CoverageSampleSelector.selectForCoverage(samples)
        // dominant bssid is either aa or bb with 2 each — pick one consistently
        assertEquals(2, selected.size)
        assertEquals(1, selected.map { it.bssid }.distinct().size)
    }

    @Test
    fun channel_2_4_isCorrect() {
        assertEquals(1, WifiChannelUtils.channelOf(2412))
        assertEquals(6, WifiChannelUtils.channelOf(2437))
        assertEquals(11, WifiChannelUtils.channelOf(2462))
        assertEquals(WifiChannelUtils.Band.BAND_2_4, WifiChannelUtils.bandOf(2437))
        assertEquals(WifiChannelUtils.Band.BAND_5, WifiChannelUtils.bandOf(5180))
    }

    @Test
    fun speedFormat_units() {
        assertEquals("12.00 Mbps", SpeedFormat.formatMbps(12f))
        assertTrue(SpeedFormat.formatMbps(0.5f).contains("kbps"))
        assertTrue(SpeedFormat.formatMbps(1500f).contains("Gbps"))
    }

    @Test
    fun rssiPercent_clamps() {
        assertEquals(0, RssiDisplay.toPercent(-120))
        assertEquals(100, RssiDisplay.toPercent(-20))
        val mid = RssiDisplay.toPercent(-65)
        assertTrue(mid in 1..99)
    }

    @Test
    fun smoothSpatially_averagesCell() {
        val samples = listOf(
            sample(0.01f, 0f, -40, id = 1),
            sample(0.02f, 0f, -60, id = 2),
        )
        val smoothed = CoverageSampleSelector.smoothSpatially(samples, cellSizeM = 0.5f)
        assertEquals(1, smoothed.size)
        assertEquals(-50, smoothed.first().rssiDbm)
    }
}
