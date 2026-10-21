package de.bavaco.gt7.ui

import de.bavaco.gt7.session.LapSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartScaleTest {
    @Test fun niceAxisRoundsToStep() {
        assertEquals(listOf(0f, 50f, 100f, 150f, 200f, 250f), niceAxis(0f, 237f))
    }

    @Test fun niceAxisCoversNegativeRange() {
        val axis = niceAxis(-0.8f, 1.3f)
        assertTrue(axis.first() <= -0.8f)
        assertTrue(axis.last() >= 1.3f)
    }

    @Test fun niceAxisHandlesDegenerateRange() {
        val axis = niceAxis(5f, 5f)
        assertTrue(axis.size >= 2 && axis.first() <= 5f && axis.last() >= 5f)
    }

    @Test fun gainLossClassification() {
        assertEquals(Trend.GAIN, classifyTrend(-30))
        assertEquals(Trend.LOSS, classifyTrend(30))
        assertEquals(Trend.NEUTRAL, classifyTrend(2))
        assertEquals(Trend.NEUTRAL, classifyTrend(-2))
    }

    @Test fun segmentTrendsFollowDeltaSlope() {
        val delta = listOf(0f to 0, 10f to -20, 20f to -40, 30f to -10)
        assertEquals(listOf(Trend.GAIN, Trend.GAIN, Trend.LOSS), segmentTrends(delta, listOf(5f, 15f, 25f)))
    }

    @Test fun projectionKeepsAspectAndDoesNotMirror() {
        fun s(x: Float, z: Float) = LapSample(0, 0f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f).copy(x = x, z = z)
        val pts = projectPath(listOf(s(0f, 0f), s(100f, 50f)), 200f, 200f, 0f)
        assertEquals(2, pts.size)
        // wide track: x spans full width, scale 2; GT7's z already grows towards the bottom of the map, flipping it mirrors the track
        assertEquals(0f, pts[0].first); assertEquals(200f, pts[1].first)
        assertTrue(pts[1].second > pts[0].second)
        assertEquals(100f, pts[1].second - pts[0].second)
    }

    @Test fun projectionMatchesProjectPath() {
        val lap = listOf(
            LapSample(0, 0f, 0f, 0f, 0f, 1, 0f, -40f, 10f, 0f),
            LapSample(100, 0f, 0f, 0f, 0f, 1, 0f, 60f, 90f, 0f),
            LapSample(200, 0f, 0f, 0f, 0f, 1, 0f, 20f, -30f, 0f),
        )
        val expected = projectPath(lap, 300f, 200f, 12f)
        val projection = MapProjection(lap.map { it.x }, lap.map { it.z }, 300f, 200f, 12f)
        assertEquals(expected, lap.map { projection.map(it.x, it.z) })
    }

    @Test fun seriesValueInterpolatesOrHoldsSteps() {
        val pts = listOf(0f to 0f, 10f to 100f, 20f to 50f)
        assertEquals(25f, seriesValueAt(pts, 2.5f, steps = false))
        assertEquals(75f, seriesValueAt(pts, 15f, steps = false))
        assertEquals(100f, seriesValueAt(pts, 15f, steps = true))
        assertEquals(50f, seriesValueAt(pts, 20f, steps = true))
        assertEquals(null, seriesValueAt(pts, 21f, steps = false))
        assertEquals(null, seriesValueAt(emptyList(), 0f, steps = false))
    }
}
