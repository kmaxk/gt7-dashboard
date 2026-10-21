package de.bavaco.gt7.ui

import de.bavaco.gt7.session.LapSample
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** "Round" axis values covering [min]..[max], about [ticks] intervals, e.g. (0, 237) -> 0, 50, ..., 250. */
fun niceAxis(min: Float, max: Float, ticks: Int = 5): List<Float> {
    var lo = min
    var hi = max
    if (hi - lo < 1e-6f) { lo -= 0.5f; hi += 0.5f }
    val raw = (hi - lo) / max(1, ticks)
    val mag = 10f.pow(floor(log10(raw)))
    val f = raw / mag
    val step = mag * when { f <= 1f -> 1f; f <= 2f -> 2f; f <= 5f -> 5f; else -> 10f }
    val first = floor(lo / step).roundToInt()
    val last = ceil(hi / step).roundToInt()
    return (first..last).map { it * step }
}

/** Decimal places that keep tick labels of [axis] distinct. */
fun axisDecimals(axis: List<Float>): Int {
    val step = if (axis.size > 1) abs(axis[1] - axis[0]) else 1f
    return when { step >= 1f -> 0; step >= 0.1f -> 1; else -> 2 }
}

/** y of a polyline sorted by x at [x]: interpolated, or with [steps] the value of the point at or before [x]; null outside. */
fun seriesValueAt(points: List<Pair<Float, Float>>, x: Float, steps: Boolean): Float? {
    if (points.isEmpty() || x < points.first().first || x > points.last().first) return null
    val i = points.indexOfFirst { it.first >= x }
    val (x1, y1) = points[i]
    if (i == 0 || x1 == x) return y1
    val (x0, y0) = points[i - 1]
    if (steps || x1 <= x0) return y0
    return y0 + (y1 - y0) * ((x - x0) / (x1 - x0))
}

enum class Trend { GAIN, LOSS, NEUTRAL }

/** Delta change (ms per 10 m) below this magnitude counts as neutral. */
private const val NEUTRAL_MS = 5

/** Negative slope = lap A gains time against B, positive = loses. */
fun classifyTrend(slopeMsPer10m: Int): Trend = when {
    slopeMsPer10m <= -NEUTRAL_MS -> Trend.GAIN
    slopeMsPer10m >= NEUTRAL_MS -> Trend.LOSS
    else -> Trend.NEUTRAL
}

/** Trend of the delta curve at each of [distances] (slope of the delta segment containing it). */
fun segmentTrends(delta: List<Pair<Float, Int>>, distances: List<Float>): List<Trend> {
    if (delta.size < 2) return distances.map { Trend.NEUTRAL }
    var i = 0
    return distances.map { d ->
        while (i < delta.size - 2 && delta[i + 1].first < d) i++
        val (d0, v0) = delta[i]
        val (d1, v1) = delta[i + 1]
        val span = d1 - d0
        if (span <= 0f) Trend.NEUTRAL else classifyTrend(((v1 - v0) * 10f / span).roundToInt())
    }
}

/** Maps track coordinates into a [width] x [height] box with [pad] margin, same scale on both axes. z is not flipped: GT7's z already grows towards the bottom of the map. */
class MapProjection(xs: List<Float>, zs: List<Float>, private val width: Float, private val height: Float, pad: Float) {
    private val minX = xs.minOrNull() ?: 0f
    private val minZ = zs.minOrNull() ?: 0f
    private val scale: Float
    private val offX: Float
    private val offY: Float

    init {
        val w = max((xs.maxOrNull() ?: 0f) - minX, 1e-3f)
        val h = max((zs.maxOrNull() ?: 0f) - minZ, 1e-3f)
        scale = min((width - 2 * pad) / w, (height - 2 * pad) / h)
        offX = (width - w * scale) / 2f
        offY = (height - h * scale) / 2f
    }

    fun map(x: Float, z: Float): Pair<Float, Float> = offX + (x - minX) * scale to offY + (z - minZ) * scale
}

/** Scales x/z of [samples] into a [width] x [height] box with [pad] margin, same scale on both axes. */
fun projectPath(samples: List<LapSample>, width: Float, height: Float, pad: Float): List<Pair<Float, Float>> {
    if (samples.isEmpty()) return emptyList()
    val projection = MapProjection(samples.map { it.x }, samples.map { it.z }, width, height, pad)
    return samples.map { projection.map(it.x, it.z) }
}
