package de.stiller.gt7.session

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrackMatcherTest {
    /** Closed lap on an ellipse (semi axes a, b), distance = accumulated arc length, optional +-noise metres. */
    private fun lap(a: Float, b: Float, n: Int, noise: Float = 0f, seed: Int = 1): List<LapSample> {
        val rnd = Random(seed)
        val pts = (0..n).map { i ->
            val t = 2.0 * PI * i / n
            floatArrayOf((a * cos(t)).toFloat(), (b * sin(t)).toFloat())
        }
        var dist = 0f
        return pts.mapIndexed { i, p ->
            if (i > 0) {
                val dx = p[0] - pts[i - 1][0]
                val dz = p[1] - pts[i - 1][1]
                dist += sqrt(dx * dx + dz * dz)
            }
            val nx = if (noise > 0f) (rnd.nextFloat() * 2 - 1) * noise else 0f
            val nz = if (noise > 0f) (rnd.nextFloat() * 2 - 1) * noise else 0f
            LapSample(i * 10, dist, 0f, 0f, 0f, 0, 0f, p[0] + nx, p[1] + nz, 0f)
        }
    }

    private val circle = lap(500f, 500f, 500)

    @Test
    fun outlineHasTwoTimesPointsValuesStartingAtOrigin() {
        val o = TrackMatcher.outlineOf(circle)
        assertEquals(400, o.size)
        assertEquals(0f, o[0])
        assertEquals(0f, o[1])
    }

    @Test
    fun outlineNeedsTwoSamples() {
        assertFailsWith<IllegalArgumentException> { TrackMatcher.outlineOf(circle.take(1)) }
    }

    @Test
    fun meanDistanceRequiresSameSize() {
        assertFailsWith<IllegalArgumentException> { TrackMatcher.meanDistance(FloatArray(4), FloatArray(6)) }
        assertEquals(5f, TrackMatcher.meanDistance(floatArrayOf(0f, 0f), floatArrayOf(3f, 4f)))
    }

    @Test
    fun noisyCircleWithOtherDensityMatches() {
        val track = Track(1, "Circle", TrackMatcher.outlineOf(circle))
        val driven = TrackMatcher.outlineOf(lap(500f, 500f, 1300, noise = 3f, seed = 7))
        assertSame(track, TrackMatcher.findMatch(driven, listOf(track)))
    }

    @Test
    fun ovalDoesNotMatchCircle() {
        val track = Track(1, "Circle", TrackMatcher.outlineOf(circle))
        val oval = TrackMatcher.outlineOf(lap(400f, 150f, 900))
        assertNull(TrackMatcher.findMatch(oval, listOf(track)))
    }

    @Test
    fun closerCandidateWins() {
        val circleTrack = Track(1, "Circle", TrackMatcher.outlineOf(circle))
        val ovalTrack = Track(2, "Oval", TrackMatcher.outlineOf(lap(400f, 150f, 900)))
        val driven = TrackMatcher.outlineOf(lap(500f, 500f, 1300, noise = 3f, seed = 7))
        assertEquals(1L, TrackMatcher.findMatch(driven, listOf(ovalTrack, circleTrack))?.id)
        assertTrue(TrackMatcher.meanDistance(driven, circleTrack.outline) < TrackMatcher.meanDistance(driven, ovalTrack.outline))
    }
}
