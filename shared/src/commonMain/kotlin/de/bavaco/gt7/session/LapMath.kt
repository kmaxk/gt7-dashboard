package de.bavaco.gt7.session

import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_SLIP_SPEED_MPS = 10f / 3.6f
private const val SLIP_ALERT = 0.15f

/** Linear interpolation of lap time at [distanceM]; null if outside the sampled range. */
fun timeAtDistance(samples: List<LapSample>, distanceM: Float): Float? {
    if (samples.isEmpty()) return null
    if (distanceM < samples.first().distanceM || distanceM > samples.last().distanceM) return null
    // first index with distance >= distanceM (distances are non-decreasing)
    var lo = 0
    var hi = samples.size - 1
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (samples[mid].distanceM >= distanceM) hi = mid else lo = mid + 1
    }
    val b = samples[lo]
    if (lo == 0 || b.distanceM == distanceM) return b.timeMs.toFloat()
    val a = samples[lo - 1]
    val span = b.distanceM - a.distanceM
    if (span <= 0f) return b.timeMs.toFloat()
    return a.timeMs + (b.timeMs - a.timeMs) * ((distanceM - a.distanceM) / span)
}

/** Linear interpolation of distance at lap time [timeMs]; null if outside the sampled range. */
fun distanceAtTime(samples: List<LapSample>, timeMs: Float): Float? {
    if (samples.isEmpty()) return null
    if (timeMs < samples.first().timeMs || timeMs > samples.last().timeMs) return null
    var lo = 0
    var hi = samples.size - 1
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (samples[mid].timeMs >= timeMs) hi = mid else lo = mid + 1
    }
    val b = samples[lo]
    if (lo == 0 || b.timeMs.toFloat() == timeMs) return b.distanceM
    val a = samples[lo - 1]
    val span = b.timeMs - a.timeMs
    if (span <= 0) return b.distanceM
    return a.distanceM + (b.distanceM - a.distanceM) * ((timeMs - a.timeMs) / span)
}

fun liveDeltaMs(currentMs: Int, distanceM: Float, reference: List<LapSample>?): Int? {
    if (reference.isNullOrEmpty()) return null
    val t = timeAtDistance(reference, distanceM) ?: return null
    return (currentMs - t).roundToInt()
}

private const val FUEL_WINDOW = 5
private const val FUEL_OUTLIER = 0.25f

/**
 * Remaining laps from the average of the last (up to 3) fuel usages; null if unknown.
 * With 3+ laps, usages more than 25 % off the median of the last 5 (warm-up, pit lane) are ignored.
 */
fun fuelLapsRemaining(fuelLevel: Float, recentFuelUsed: List<Float>): Float? {
    val window = recentFuelUsed.takeLast(FUEL_WINDOW)
    val kept = if (window.size < 3) window else {
        val sorted = window.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        window.filter { abs(it - median) <= median * FUEL_OUTLIER }
    }
    val last = kept.takeLast(3)
    if (last.isEmpty()) return null
    val avg = last.sum() / last.size
    if (avg <= 0f) return null
    return fuelLevel / avg
}

/**
 * Fuel laps left minus race laps left (positive = fuel suffices); null outside races or without a fuel estimate.
 * Progress in the current lap comes from [referenceLengthM]; without it the whole lap is counted.
 */
fun fuelMarginLaps(fuelLaps: Float?, currentLap: Int, totalLaps: Int, distanceM: Float, referenceLengthM: Float?): Float? {
    if (fuelLaps == null || totalLaps <= 0 || currentLap <= 0) return null
    val progress = if (referenceLengthM != null && referenceLengthM > 0f) (distanceM / referenceLengthM).coerceIn(0f, 1f) else 0f
    return fuelLaps - (totalLaps - currentLap + 1 - progress)
}

fun wheelSlip(wheelRps: Float, radiusM: Float, speedMps: Float): Float? {
    if (speedMps < MIN_SLIP_SPEED_MPS) return null
    return abs(wheelRps) * radiusM / speedMps - 1f
}

/** GT7's rev-limit alert is reached: time to shift. [alertMax] 0 means GT7 sent no shift point. */
fun isShiftPoint(rpm: Float, alertMax: Int): Boolean = alertMax > 0 && rpm >= alertMax

fun isSlipAlert(slip: Float?): Boolean = slip != null && abs(slip) >= SLIP_ALERT

/** Time difference (ms) to [reference] every [stepM]; positive = [lap] slower. */
fun deltaCurve(lap: List<LapSample>, reference: List<LapSample>, stepM: Float = 10f): List<Pair<Float, Int>> {
    if (lap.isEmpty() || reference.isEmpty() || stepM <= 0f) return emptyList()
    val max = minOf(lap.last().distanceM, reference.last().distanceM)
    val out = ArrayList<Pair<Float, Int>>()
    var i = 0
    while (true) {
        val d = i * stepM
        if (d > max) break
        val a = timeAtDistance(lap, d)
        val b = timeAtDistance(reference, d)
        if (a != null && b != null) out += d to (a - b).roundToInt()
        i++
    }
    return out
}

/** Position (x, z) of [samples] at lap time [timeMs], linearly interpolated; null outside the lap. */
fun positionAtTime(samples: List<LapSample>, timeMs: Int): Pair<Float, Float>? {
    if (samples.isEmpty() || timeMs < samples.first().timeMs || timeMs > samples.last().timeMs) return null
    val i = samples.indexOfFirst { it.timeMs >= timeMs }
    val b = samples[i]
    if (i == 0 || b.timeMs == timeMs) return b.x to b.z
    val a = samples[i - 1]
    val f = (timeMs - a.timeMs).toFloat() / (b.timeMs - a.timeMs)
    return a.x + (b.x - a.x) * f to a.z + (b.z - a.z) * f
}

enum class GapTrend { CLOSING, LOSING, STEADY }

private const val GAP_TREND_MIN_MS = 20

/** Whether the gap to the reference shrinks or grows compared with [deltaBeforeMs]; small changes are steady. */
fun gapTrend(deltaNowMs: Int?, deltaBeforeMs: Int?): GapTrend {
    if (deltaNowMs == null || deltaBeforeMs == null) return GapTrend.STEADY
    val change = deltaNowMs - deltaBeforeMs
    return when {
        change <= -GAP_TREND_MIN_MS -> GapTrend.CLOSING
        change >= GAP_TREND_MIN_MS -> GapTrend.LOSING
        else -> GapTrend.STEADY
    }
}
