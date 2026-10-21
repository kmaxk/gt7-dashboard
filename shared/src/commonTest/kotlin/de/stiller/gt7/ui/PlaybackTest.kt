package de.stiller.gt7.ui

import de.stiller.gt7.session.LapSample
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTest {
    /** Straight along x; [msPer10m] per 10 m over 100 m. */
    private fun lap(msPer10m: Int) = (0..10).map { i -> LapSample(i * msPer10m, i * 10f, 0f, 0f, 0f, 3, 0f, i * 10f, 0f, 0f) }

    private val a = lap(100) // 1.0 s
    private val b = lap(200) // 2.0 s

    @Test
    fun bothLapsRunOnTheirOwnClock() {
        val f = playbackFrame(a, b, 500)
        assertEquals(50f to 0f, f.a)
        assertEquals(25f to 0f, f.b)
        assertEquals(50f, f.cursorDistanceM)
    }

    @Test
    fun aFinishedLapWaitsAtTheLine() {
        val f = playbackFrame(a, b, 1500)
        assertEquals(100f to 0f, f.a)
        assertEquals(75f to 0f, f.b)
        assertEquals(100f, f.cursorDistanceM)
    }

    @Test
    fun playbackEndsWithTheLongerLap() {
        assertEquals(2000, playbackEndMs(a, b))
        assertEquals(1000, playbackEndMs(a, null))
        assertEquals(0, playbackEndMs(emptyList(), null))
    }

    @Test
    fun withoutLapBOnlyAMoves() {
        val f = playbackFrame(a, null, 200)
        assertEquals(20f to 0f, f.a)
        assertEquals(null, f.b)
    }
}
