package de.stiller.gt7.session

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LapMathTest {
    private fun s(timeMs: Int, d: Float) = LapSample(timeMs, d, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f)
    private val ref = listOf(s(0, 0f), s(2000, 100f))

    @Test
    fun timeAtDistanceInterpolates() {
        assertEquals(1000f, timeAtDistance(ref, 50f))
        assertNull(timeAtDistance(ref, 150f))
        assertNull(timeAtDistance(emptyList(), 0f))
    }

    @Test
    fun liveDelta() {
        assertEquals(200, liveDeltaMs(1200, 50f, ref))
        assertNull(liveDeltaMs(1200, 50f, null))
        assertNull(liveDeltaMs(1200, 150f, ref))
    }

    @Test
    fun fuelLaps() {
        assertEquals(10f, fuelLapsRemaining(30f, listOf(5f, 3f, 3f, 3f)))
        assertNull(fuelLapsRemaining(30f, listOf()))
        assertNull(fuelLapsRemaining(30f, listOf(0f)))
    }

    @Test
    fun slip() {
        assertTrue(abs(wheelSlip(50f, 0.33f, 15f)!! - 0.1f) < 0.001f)
        assertNull(wheelSlip(50f, 0.33f, 2f))
        assertTrue(abs(wheelSlip(-50f, 0.33f, 15f)!! - 0.1f) < 0.001f)
        assertTrue(isSlipAlert(0.15f))
        assertTrue(isSlipAlert(0.16f))
        assertFalse(isSlipAlert(0.14f))
        assertTrue(isSlipAlert(-0.2f))
        assertFalse(isSlipAlert(null))
    }

    @Test
    fun deltaCurveAgainstFasterReference() {
        val lap = (0..10).map { s(it * 1000, it * 100f) }          // 1000 m in 10 s
        val faster = (0..10).map { s(it * 900, it * 100f) }        // 10 % faster
        val curve = deltaCurve(lap, faster)
        assertEquals(101, curve.size)
        assertEquals(0, curve.first().second)
        assertTrue(curve.last().second > 0)
        assertEquals(1000, curve.last().second)
    }

    @Test fun shiftPointFromGt7Alert() {
        assertFalse(isShiftPoint(8399f, 8400))
        assertTrue(isShiftPoint(8400f, 8400))
        assertTrue(isShiftPoint(8650f, 8400))
        assertFalse(isShiftPoint(9000f, 0))
    }

    @Test fun fuelIgnoresOutlierLap() {
        // warm-up lap with 1.2 % would otherwise give 30 / 2.4 = 12.5 laps
        assertEquals(10f, fuelLapsRemaining(30f, listOf(3f, 1.2f, 3f)))
    }

    @Test fun fuelKeepsFewLapsUnfiltered() {
        assertEquals(30f / 2.1f, fuelLapsRemaining(30f, listOf(1.2f, 3f))!!, 0.001f)
    }

    @Test fun fuelMarginWithReferenceProgress() {
        // lap 8 of 10, half way through: 2.5 race laps to go
        assertEquals(2.5f, fuelMarginLaps(5f, 8, 10, 2500f, 5000f)!!, 0.001f)
        assertEquals(-1f, fuelMarginLaps(1.5f, 8, 10, 2500f, 5000f)!!, 0.001f)
    }

    @Test fun fuelMarginWithoutReferenceCountsWholeLap() {
        assertEquals(2f, fuelMarginLaps(5f, 8, 10, 2500f, null)!!, 0.001f)
    }

    @Test fun fuelMarginUnknownOutsideRace() {
        assertNull(fuelMarginLaps(5f, 3, 0, 100f, 5000f))
        assertNull(fuelMarginLaps(null, 8, 10, 100f, 5000f))
    }

    private fun at(t: Int, x: Float, z: Float) = LapSample(t, 0f, 0f, 0f, 0f, 1, 0f, x, z, 0f)

    @Test fun positionInterpolatesByTime() {
        val s = listOf(at(0, 0f, 0f), at(1000, 10f, -20f))
        assertEquals(5f to -10f, positionAtTime(s, 500))
    }

    @Test fun positionOutsideLapIsNull() {
        val s = listOf(at(0, 0f, 0f), at(1000, 10f, -20f))
        assertNull(positionAtTime(s, 1001))
        assertNull(positionAtTime(emptyList(), 0))
    }

    @Test fun gapTrendDirections() {
        assertEquals(GapTrend.CLOSING, gapTrend(-200, -100))
        assertEquals(GapTrend.LOSING, gapTrend(150, 100))
        assertEquals(GapTrend.STEADY, gapTrend(105, 100))
        assertEquals(GapTrend.STEADY, gapTrend(null, 100))
    }
}
