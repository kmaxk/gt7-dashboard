package de.stiller.gt7.session

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LapStatsTest {
    private fun speeds(vararg v: Float) = v.mapIndexed { i, mps -> LapSample(i * 100, i * 10f, mps, 0f, 0f, 3, 0f, 0f, 0f, 0f) }

    private fun inputs(vararg ti: Pair<Int, Pair<Float, Float>>) =
        ti.map { (t, tb) -> LapSample(t, t / 10f, 30f, tb.first, tb.second, 3, 0f, 0f, 0f, 0f) }

    @Test
    fun peaksAndValleysFollowTheSpeedTrace() {
        // straight to 60, corner at 20, straight to 50, corner at 25
        val pv = peaksAndValleys(speeds(30f, 45f, 60f, 40f, 20f, 35f, 50f, 40f, 25f, 30f))
        assertEquals(listOf(60f, 50f), pv.peaks.map { it.speedMps })
        assertEquals(listOf(20f, 25f), pv.valleys.map { it.speedMps })
        assertEquals(listOf(20f, 60f), pv.peaks.map { it.distanceM })
        assertEquals(listOf(40f, 80f), pv.valleys.map { it.distanceM })
    }

    @Test
    fun smallWigglesAreIgnored() {
        val pv = peaksAndValleys(speeds(30f, 50f, 49f, 50.5f, 48f, 51f, 20f, 40f), minProminenceMps = 3f)
        assertEquals(listOf(51f), pv.peaks.map { it.speedMps })
        assertEquals(listOf(20f), pv.valleys.map { it.speedMps })
    }

    @Test
    fun noExtremesOnShortOrFlatLaps() {
        assertTrue(peaksAndValleys(emptyList()).peaks.isEmpty())
        val flat = peaksAndValleys(speeds(30f, 31f, 30f, 31f))
        assertTrue(flat.peaks.isEmpty() && flat.valleys.isEmpty())
    }

    @Test
    fun inputSharesWeightByTime() {
        // 0–300 full throttle, 300–400 full brake, 400–1000 coasting
        val s = inputs(0 to (1f to 0f), 300 to (0f to 1f), 400 to (0f to 0f), 1000 to (0f to 0f))
        val shares = inputShares(s)
        assertTrue(abs(shares.fullThrottle - 0.3f) < 1e-4f, "${shares.fullThrottle}")
        assertTrue(abs(shares.fullBrake - 0.1f) < 1e-4f, "${shares.fullBrake}")
        assertTrue(abs(shares.coasting - 0.6f) < 1e-4f, "${shares.coasting}")
    }

    @Test
    fun inputSharesOfEmptyLapAreZero() {
        assertEquals(InputShares(0f, 0f, 0f), inputShares(emptyList()))
    }

    @Test
    fun coastingIsOneWithoutPedals() {
        val s = inputs(0 to (0.5f to 0f), 100 to (0.01f to 0.01f), 200 to (0f to 0.3f))
        assertEquals(listOf(0f, 1f, 0f), s.map { coasting(it) })
    }

    /** 300 m, 1 s per 10 m in the first third, 2 s in the second, 0.5 s in the last. */
    private val thirds = (0..30).map { i ->
        val t = when {
            i <= 10 -> i * 1000
            i <= 20 -> 10_000 + (i - 10) * 2000
            else -> 30_000 + (i - 20) * 500
        }
        LapSample(t, i * 10f, i.toFloat(), 0f, 0f, 3, 0f, 0f, 0f, 0f)
    }

    @Test
    fun vmaxIsTheTopSpeedInKmh() {
        assertEquals(108f, vmaxKmh(thirds)!!, 1e-3f)
        assertEquals(null, vmaxKmh(emptyList()))
    }

    @Test
    fun sectorsSplitTheLapInThirds() {
        assertEquals(listOf(100f, 200f), sectorBoundaries(300f))
        assertEquals(listOf(10_000, 20_000, 5_000), sectorTimes(thirds, sectorBoundaries(300f)))
    }

    @Test
    fun aLapShortOfABoundaryHasNoSectorTimes() {
        assertEquals(null, sectorTimes(thirds.take(15), sectorBoundaries(300f)))
        assertEquals(null, sectorTimes(emptyList(), sectorBoundaries(300f)))
    }

    @Test
    fun theoreticalBestAddsTheBestOfEachSector() {
        assertEquals(29_000, theoreticalBest(listOf(listOf(10_000, 12_000, 9_000), listOf(11_000, 11_000, 8_000))))
        assertEquals(null, theoreticalBest(emptyList()))
    }
}
