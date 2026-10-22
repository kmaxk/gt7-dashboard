package de.bavaco.gt7.session

import kotlin.math.roundToInt

private const val FULL_PEDAL = 0.98f
private const val NO_PEDAL = 0.02f

/** A speed extreme of a lap: peaks end straights, valleys are corner apexes. */
data class SpeedPoint(val speedMps: Float, val distanceM: Float)

data class PeaksAndValleys(val peaks: List<SpeedPoint>, val valleys: List<SpeedPoint>)

/** Shares (0..1) of lap time spent at full throttle, full brake and with neither pedal. */
data class InputShares(val fullThrottle: Float, val fullBrake: Float, val coasting: Float)

/**
 * Speed peaks and valleys as a zigzag over the speed trace: an extreme counts once the speed has
 * moved [minProminenceMps] away from it, so small wiggles are ignored.
 */
fun peaksAndValleys(samples: List<LapSample>, minProminenceMps: Float = 3f): PeaksAndValleys {
    val peaks = ArrayList<SpeedPoint>()
    val valleys = ArrayList<SpeedPoint>()
    if (samples.isEmpty()) return PeaksAndValleys(peaks, valleys)
    var hi = samples.first()
    var lo = samples.first()
    var rising: Boolean? = null // unknown until the first move of minProminenceMps
    for (s in samples) {
        if (s.speedMps > hi.speedMps) hi = s
        if (s.speedMps < lo.speedMps) lo = s
        when (rising) {
            null -> when {
                s.speedMps - lo.speedMps >= minProminenceMps -> { rising = true; hi = s }
                hi.speedMps - s.speedMps >= minProminenceMps -> { rising = false; lo = s }
            }
            true -> if (hi.speedMps - s.speedMps >= minProminenceMps) {
                peaks += SpeedPoint(hi.speedMps, hi.distanceM)
                rising = false
                lo = s
            }
            false -> if (s.speedMps - lo.speedMps >= minProminenceMps) {
                valleys += SpeedPoint(lo.speedMps, lo.distanceM)
                rising = true
                hi = s
            }
        }
    }
    return PeaksAndValleys(peaks, valleys)
}

/** 1 when neither pedal is pressed, else 0; for coasting charts. */
fun coasting(s: LapSample): Float = if (s.throttle < NO_PEDAL && s.brake < NO_PEDAL) 1f else 0f

/** Each sample's pedal state holds until the next sample, so shares are weighted by time. */
fun inputShares(samples: List<LapSample>): InputShares {
    var throttle = 0L
    var brake = 0L
    var coast = 0L
    var total = 0L
    for (i in 1 until samples.size) {
        val s = samples[i - 1]
        val dt = (samples[i].timeMs - s.timeMs).toLong().coerceAtLeast(0L)
        total += dt
        if (s.throttle >= FULL_PEDAL) throttle += dt
        if (s.brake >= FULL_PEDAL) brake += dt
        if (coasting(s) == 1f) coast += dt
    }
    if (total == 0L) return InputShares(0f, 0f, 0f)
    return InputShares(throttle.toFloat() / total, brake.toFloat() / total, coast.toFloat() / total)
}

private const val SECTORS = 3

/** Top speed of the lap in km/h; null without samples. */
fun vmaxKmh(samples: List<LapSample>): Float? = samples.maxOfOrNull { it.speedMps }?.let { it * 3.6f }

/** Distances that split a lap of [lengthM] into three equal sectors. */
fun sectorBoundaries(lengthM: Float): List<Float> = (1 until SECTORS).map { lengthM * it / SECTORS }

/** Time in each sector between [boundaries] (ms); null if the lap doesn't reach every boundary. */
fun sectorTimes(samples: List<LapSample>, boundaries: List<Float>): List<Int>? {
    if (samples.size < 2) return null
    val marks = boundaries.map { timeAtDistance(samples, it) ?: return null }
    val edges = listOf(samples.first().timeMs.toFloat()) + marks + samples.last().timeMs.toFloat()
    return edges.zipWithNext { a, b -> (b - a).roundToInt() }
}

/** Sum of the best time of each sector over [sectors] (one list per lap); null without laps. */
fun theoreticalBest(sectors: List<List<Int>>): Int? {
    if (sectors.isEmpty()) return null
    return sectors.first().indices.sumOf { i -> sectors.minOf { it[i] } }
}

/**
 * [measured] sector times stretched to add up to GT7's [lapTimeMs]: the samples' clock runs slightly apart from GT7's,
 * and unscaled sectors could make the theoretical best slower than the lap itself.
 */
fun scaleSectors(measured: List<Int>, lapTimeMs: Int): List<Int> {
    val total = measured.sum()
    if (total <= 0 || lapTimeMs <= 0) return measured
    val scaled = measured.dropLast(1).map { (it.toLong() * lapTimeMs / total).toInt() }
    return scaled + (lapTimeMs - scaled.sum())
}

/** Theoretical best of valid [laps] (time in ms and samples); sector boundaries come from the fastest lap. Null without a usable lap. */
fun theoreticalBestOfLaps(laps: List<Pair<Int, List<LapSample>>>): Int? {
    val length = laps.minByOrNull { it.first }?.second?.lastOrNull()?.distanceM ?: return null
    val boundaries = sectorBoundaries(length)
    return theoreticalBest(laps.mapNotNull { (timeMs, samples) -> sectorTimes(samples, boundaries)?.let { scaleSectors(it, timeMs) } })
}
