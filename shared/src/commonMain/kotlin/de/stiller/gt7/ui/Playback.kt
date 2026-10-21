package de.stiller.gt7.ui

import de.stiller.gt7.session.LapSample
import de.stiller.gt7.session.distanceAtTime
import de.stiller.gt7.session.positionAtTime

/** Map positions (x, z) of A and B at one moment of playback, and A's distance for the chart cursor. */
data class PlaybackFrame(val a: Pair<Float, Float>?, val b: Pair<Float, Float>?, val cursorDistanceM: Float?)

/** Playback runs until the longer lap is over. */
fun playbackEndMs(a: List<LapSample>, b: List<LapSample>?): Int =
    maxOf(a.lastOrNull()?.timeMs ?: 0, b?.lastOrNull()?.timeMs ?: 0)

/** A and B each at their own lap time [tMs]; a lap that is over waits at the finish line. */
fun playbackFrame(a: List<LapSample>, b: List<LapSample>?, tMs: Int): PlaybackFrame {
    fun at(l: List<LapSample>): Int = tMs.coerceIn(l.firstOrNull()?.timeMs ?: 0, l.lastOrNull()?.timeMs ?: 0)
    return PlaybackFrame(
        a = positionAtTime(a, at(a)),
        b = b?.let { positionAtTime(it, at(it)) },
        cursorDistanceM = distanceAtTime(a, at(a).toFloat()),
    )
}
