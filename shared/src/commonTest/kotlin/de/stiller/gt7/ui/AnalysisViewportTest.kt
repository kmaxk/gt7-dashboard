package de.stiller.gt7.ui

import de.stiller.gt7.session.Corner
import de.stiller.gt7.session.LapSample
import de.stiller.gt7.session.distanceAtTime
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnalysisViewportTest {
    /** Every 10 m; the first 100 m take 100 ms per sample, then 200 ms; gear goes up every 50 m. */
    private val lap: List<LapSample> = (0..20).map { i ->
        val t = if (i <= 10) i * 100 else 1000 + (i - 10) * 200
        LapSample(t, i * 10f, i.toFloat(), 0f, 0f, 1 + i / 5, 1000f * i, i * 2f, -i.toFloat(), 0f)
    }

    private fun close(expected: Float, actual: Float?, tolerance: Float = 1e-3f) {
        requireNotNull(actual)
        assertEquals(expected, actual, tolerance)
    }

    @Test
    fun sampleAtInterpolatesByDistance() {
        val s = sampleAt(lap, 25f, AxisMode.DISTANCE)!!
        close(2.5f, s.speedMps)
        close(2500f, s.rpm)
        assertEquals(250, s.timeMs)
        // gear holds the earlier sample's value until the next sample
        assertEquals(1, sampleAt(lap, 49f, AxisMode.DISTANCE)!!.gear)
        assertEquals(2, sampleAt(lap, 50f, AxisMode.DISTANCE)!!.gear)
    }

    @Test
    fun sampleAtInterpolatesByTime() {
        // 1.1 s lies halfway between 100 m (1.0 s) and 110 m (1.2 s)
        val s = sampleAt(lap, 1.1f, AxisMode.TIME)!!
        close(105f, s.distanceM)
        close(10.5f, s.speedMps)
    }

    @Test
    fun sampleAtIsNullOutsideTheLap() {
        assertNull(sampleAt(lap, -1f, AxisMode.DISTANCE))
        assertNull(sampleAt(lap, 201f, AxisMode.DISTANCE))
        assertNull(sampleAt(lap, 3.1f, AxisMode.TIME))
        assertNull(sampleAt(emptyList(), 0f, AxisMode.TIME))
    }

    @Test
    fun distanceAtTimeInvertsTimeAtDistance() {
        close(105f, distanceAtTime(lap, 1100f))
        close(0f, distanceAtTime(lap, 0f))
        assertNull(distanceAtTime(lap, 5000f))
    }

    @Test
    fun fullRangeCoversTheLongerLap() {
        assertEquals(0f..200f, fullRange(lap, lap.take(5), AxisMode.DISTANCE))
        assertEquals(0f..3f, fullRange(lap, null, AxisMode.TIME))
    }

    @Test
    fun zoomKeepsTheAnchorInPlace() {
        val z = zoomRange(0f..200f, 0f..200f, anchor = 50f, factor = 0.5f, minSpan = 20f)
        assertEquals(25f..125f, z)
    }

    @Test
    fun zoomStaysInsideTheLapAndAboveTheMinimumSpan() {
        assertEquals(0f..200f, zoomRange(0f..100f, 0f..200f, anchor = 50f, factor = 4f, minSpan = 20f))
        assertEquals(40f..60f, zoomRange(45f..55f, 0f..200f, anchor = 50f, factor = 0.5f, minSpan = 20f))
        // zooming out near the end shifts the window back inside
        assertEquals(100f..200f, zoomRange(150f..200f, 0f..200f, anchor = 200f, factor = 2f, minSpan = 20f))
    }

    @Test
    fun panStopsAtTheEdges() {
        assertEquals(30f..80f, panRange(10f..60f, 0f..200f, 20f))
        assertEquals(0f..50f, panRange(10f..60f, 0f..200f, -30f))
        assertEquals(150f..200f, panRange(140f..190f, 0f..200f, 30f))
    }

    @Test
    fun convertRangeKeepsTheSameStretchOfTrack() {
        val t = convertRange(50f..150f, AxisMode.DISTANCE, AxisMode.TIME, lap)!!
        close(0.5f, t.start); close(2.0f, t.endInclusive)
        val back = convertRange(t, AxisMode.TIME, AxisMode.DISTANCE, lap)!!
        close(50f, back.start); close(150f, back.endInclusive)
        assertEquals(50f..150f, convertRange(50f..150f, AxisMode.DISTANCE, AxisMode.DISTANCE, lap))
        assertNull(convertRange(0f..1f, AxisMode.TIME, AxisMode.DISTANCE, emptyList()))
    }

    @Test
    fun cornerRangeAddsAMargin() {
        val c = Corner(1, 60f, 100f, 160f)
        // 15 % of 100 m on each side
        assertEquals(45f..175f, cornerRange(c, AxisMode.DISTANCE, lap, 0f..200f))
        val t = cornerRange(c, AxisMode.TIME, lap, 0f..3f)
        close(0.45f, t.start); close(2.5f, t.endInclusive)
        // clamped to the lap
        assertEquals(0f..57.5f, cornerRange(Corner(1, 0f, 20f, 50f), AxisMode.DISTANCE, lap, 0f..200f))
    }

    @Test
    fun overlayAndDeltaFollowTheAxis() {
        val corners = listOf(Corner(1, 60f, 100f, 160f), Corner(2, 160f, 180f, 200f))
        assertEquals(listOf(1 to 100f, 2 to 180f), chartOverlay(corners, corners[0], AxisMode.DISTANCE, lap).markers)
        assertEquals(60f..160f, chartOverlay(corners, corners[0], AxisMode.DISTANCE, lap).shade)
        val time = chartOverlay(corners, null, AxisMode.TIME, lap)
        assertEquals(listOf(1 to 1.0f, 2 to 2.6f), time.markers.map { it.first to (it.second * 100).roundToInt() / 100f })
        assertNull(time.shade)
        // delta points move to the time at which A reached their distance
        assertEquals(listOf(1.0f to 0.25f), deltaSeries(listOf(100f to 250), AxisMode.TIME, lap))
        assertEquals(listOf(100f to 0.25f), deltaSeries(listOf(100f to 250), AxisMode.DISTANCE, lap))
    }
}
