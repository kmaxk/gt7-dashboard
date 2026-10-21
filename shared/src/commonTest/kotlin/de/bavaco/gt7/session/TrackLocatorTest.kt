package de.bavaco.gt7.session

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackLocatorTest {
    /** Circle of [radius] around ([cx], 0), one sample per metre. */
    private fun circle(radius: Float, cx: Float = 0f): List<LapSample> {
        val n = (2 * PI * radius).toInt()
        return (0 until n).map { i ->
            val a = i / radius
            LapSample(i, i.toFloat(), 0f, 0f, 0f, 0, 0f, cx + radius * cos(a), radius * sin(a), 0f)
        }
    }

    private fun TrackLocator.drive(line: List<LapSample>, metres: Int, offset: Float = 0f) =
        line.take(metres).forEach { add(it.x + offset, it.z) }

    @Test fun recognisesTrackAfterEnoughDistance() {
        val loc = TrackLocator()
        val spa = circle(200f)
        loc.setCandidates(mapOf(1L to spa, 2L to circle(200f, cx = 2000f)))
        loc.drive(spa, 200)
        assertNull(loc.locate(null)) // 40 points < 60
        loc.drive(spa.drop(200), 200)
        assertEquals(1L, loc.locate(null))
    }

    @Test fun toleratesDrivingBesideTheLine() {
        val loc = TrackLocator()
        val spa = circle(200f)
        loc.setCandidates(mapOf(1L to spa))
        loc.drive(spa, 400, offset = 5f)
        assertEquals(1L, loc.locate(null))
    }

    @Test fun unknownTrackStaysUnknown() {
        val loc = TrackLocator()
        loc.setCandidates(mapOf(1L to circle(200f)))
        loc.drive(circle(200f, cx = 3000f), 400)
        assertNull(loc.locate(null))
    }

    @Test fun sharedStretchIsUndecidedButKeepsCurrent() {
        val loc = TrackLocator()
        val line = circle(200f)
        // two layouts over the same ground
        loc.setCandidates(mapOf(1L to line, 2L to line))
        loc.drive(line, 400)
        assertNull(loc.locate(null))
        assertEquals(2L, loc.locate(2L))
    }

    @Test fun dropsCurrentWhenThePathLeavesIt() {
        val loc = TrackLocator()
        loc.setCandidates(mapOf(1L to circle(200f)))
        loc.drive(circle(200f, cx = 3000f), 400)
        assertNull(loc.locate(1L))
    }
}
