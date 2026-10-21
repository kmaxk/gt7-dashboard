package de.bavaco.gt7.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.bavaco.gt7.session.Corner
import de.bavaco.gt7.session.LapSample
import de.bavaco.gt7.session.distanceAtTime
import de.bavaco.gt7.session.timeAtDistance
import kotlin.math.max
import kotlin.math.min

/** x axis of the analysis charts: distance in m or lap time in s. */
enum class AxisMode { DISTANCE, TIME }

private const val CORNER_MARGIN = 0.15f

fun minSpan(mode: AxisMode): Float = if (mode == AxisMode.DISTANCE) 20f else 1f

fun xOf(s: LapSample, mode: AxisMode): Float = if (mode == AxisMode.DISTANCE) s.distanceM else s.timeMs / 1000f

/** All channels of [samples] at [x], linearly interpolated; gear holds the earlier sample's value. Null outside the lap. */
fun sampleAt(samples: List<LapSample>, x: Float, mode: AxisMode): LapSample? {
    if (samples.isEmpty() || x < xOf(samples.first(), mode) || x > xOf(samples.last(), mode)) return null
    var lo = 0
    var hi = samples.size - 1
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (xOf(samples[mid], mode) >= x) hi = mid else lo = mid + 1
    }
    val b = samples[lo]
    if (lo == 0 || xOf(b, mode) == x) return b
    val a = samples[lo - 1]
    val span = xOf(b, mode) - xOf(a, mode)
    if (span <= 0f) return b
    val f = (x - xOf(a, mode)) / span
    fun mix(p: Float, q: Float) = p + (q - p) * f
    return LapSample(
        timeMs = (a.timeMs + (b.timeMs - a.timeMs) * f).toInt(),
        distanceM = mix(a.distanceM, b.distanceM),
        speedMps = mix(a.speedMps, b.speedMps),
        throttle = mix(a.throttle, b.throttle),
        brake = mix(a.brake, b.brake),
        gear = a.gear,
        rpm = mix(a.rpm, b.rpm),
        x = mix(a.x, b.x),
        z = mix(a.z, b.z),
        boostBar = mix(a.boostBar, b.boostBar),
    )
}

/** 0 to the end of the longer lap, at least [minSpan] wide. */
fun fullRange(a: List<LapSample>, b: List<LapSample>?, mode: AxisMode): ClosedFloatingPointRange<Float> {
    val end = max(a.lastOrNull()?.let { xOf(it, mode) } ?: 0f, b?.lastOrNull()?.let { xOf(it, mode) } ?: 0f)
    return 0f..max(end, minSpan(mode))
}

/** Scales the span of [range] by [factor] around [anchor] (which stays put), kept inside [full] and at least [minSpan]. */
fun zoomRange(
    range: ClosedFloatingPointRange<Float>,
    full: ClosedFloatingPointRange<Float>,
    anchor: Float,
    factor: Float,
    minSpan: Float,
): ClosedFloatingPointRange<Float> {
    val span = range.endInclusive - range.start
    val fullSpan = full.endInclusive - full.start
    val newSpan = (span * factor).coerceIn(min(minSpan, fullSpan), fullSpan)
    val fraction = if (span > 0f) ((anchor - range.start) / span).coerceIn(0f, 1f) else 0.5f
    return fit(anchor - fraction * newSpan, newSpan, full)
}

/** Shifts [range] by [dx], stopping at the edges of [full]. */
fun panRange(range: ClosedFloatingPointRange<Float>, full: ClosedFloatingPointRange<Float>, dx: Float): ClosedFloatingPointRange<Float> =
    fit(range.start + dx, range.endInclusive - range.start, full)

private fun fit(start: Float, span: Float, full: ClosedFloatingPointRange<Float>): ClosedFloatingPointRange<Float> {
    val s = start.coerceIn(full.start, max(full.start, full.endInclusive - span))
    return s..min(s + span, full.endInclusive)
}

/** [x] on the [from] axis as a value on the [to] axis, by way of lap [a]; null if [a] doesn't reach it. */
fun convertX(x: Float, from: AxisMode, to: AxisMode, a: List<LapSample>): Float? = when {
    from == to -> x
    to == AxisMode.TIME -> timeAtDistance(a, x.coerceIn(a.firstOrNull()?.distanceM ?: 0f, a.lastOrNull()?.distanceM ?: 0f))?.div(1000f)
    else -> distanceAtTime(a, (x * 1000f).coerceIn(a.firstOrNull()?.timeMs?.toFloat() ?: 0f, a.lastOrNull()?.timeMs?.toFloat() ?: 0f))
}

/** The same stretch of track on the other axis, so switching axes keeps the view. */
fun convertRange(range: ClosedFloatingPointRange<Float>, from: AxisMode, to: AxisMode, a: List<LapSample>): ClosedFloatingPointRange<Float>? {
    if (from == to) return range
    val start = convertX(range.start, from, to, a) ?: return null
    val end = convertX(range.endInclusive, from, to, a) ?: return null
    return start..end
}

/** The corner's zone with a margin on each side, on [mode]'s axis (time by way of lap [a]), inside [full]. */
fun cornerRange(corner: Corner, mode: AxisMode, a: List<LapSample>, full: ClosedFloatingPointRange<Float>): ClosedFloatingPointRange<Float> {
    val margin = (corner.endM - corner.startM) * CORNER_MARGIN
    val dist = (corner.startM - margin)..(corner.endM + margin)
    val r = convertRange(dist, AxisMode.DISTANCE, mode, a) ?: return full
    val start = max(r.start, full.start)
    val end = min(r.endInclusive, full.endInclusive)
    return if (end - start >= minSpan(mode)) start..end else zoomRange(start..end, full, (start + end) / 2f, 1f, minSpan(mode))
}

/** Corner apexes as chart markers and the [selected] corner's zone as shade, on [mode]'s axis by way of lap [a]. */
fun chartOverlay(corners: List<Corner>, selected: Corner?, mode: AxisMode, a: List<LapSample>): ChartOverlay = ChartOverlay(
    markers = corners.mapNotNull { c -> convertX(c.apexM, AxisMode.DISTANCE, mode, a)?.let { c.number to it } },
    shade = selected?.let { convertRange(it.startM..it.endM, AxisMode.DISTANCE, mode, a) },
)

/** Delta curve (distance, ms) as chart points in s; on the time axis at the time lap [a] reached each distance. */
fun deltaSeries(delta: List<Pair<Float, Int>>, mode: AxisMode, a: List<LapSample>): List<Pair<Float, Float>> =
    delta.mapNotNull { (d, ms) -> convertX(d, AxisMode.DISTANCE, mode, a)?.let { it to ms / 1000f } }

/** Cursor, zoom, axis, chosen corner and playback, shared by every chart and the map of one lap pair. */
@Stable
class AnalysisViewport(val a: List<LapSample>, val b: List<LapSample>?) {
    var mode by mutableStateOf(AxisMode.DISTANCE)
        private set
    var range by mutableStateOf(fullRange(a, b, AxisMode.DISTANCE))
        private set
    /** x on the current axis, null = no cursor. */
    var cursor by mutableStateOf<Float?>(null)
        private set
    var selectedCorner by mutableStateOf<Corner?>(null)
        private set
    var playing by mutableStateOf(false)
    /** Playback clock; it places A and B by their own lap time while [fromPlayback]. */
    var playMs by mutableStateOf(0)
        private set
    var fromPlayback by mutableStateOf(false)
        private set

    val full: ClosedFloatingPointRange<Float> get() = fullRange(a, b, mode)
    val endMs: Int get() = playbackEndMs(a, b)
    val isZoomed: Boolean get() = range != full

    fun switchMode(to: AxisMode) {
        if (to == mode) return
        val newFull = fullRange(a, b, to)
        range = if (isZoomed) convertRange(range, mode, to, a)?.let { fit(it.start, it.endInclusive - it.start, newFull) } ?: newFull else newFull
        cursor = cursor?.let { convertX(it, mode, to, a) }
        mode = to
    }

    fun zoom(factor: Float, anchor: Float = (range.start + range.endInclusive) / 2f) {
        range = zoomRange(range, full, anchor, factor, minSpan(mode))
    }

    fun pan(dx: Float) {
        range = panRange(range, full, dx)
    }

    fun resetZoom() {
        range = full
    }

    /** Cursor set by hand: stops playback. */
    fun moveCursor(x: Float?) {
        playing = false
        fromPlayback = false
        cursor = x
    }

    /** Selects [corner] and zooms to it; the selected corner again clears the selection and keeps the zoom. */
    fun toggleCorner(corner: Corner) {
        if (selectedCorner == corner) {
            selectedCorner = null
            return
        }
        selectedCorner = corner
        range = cornerRange(corner, mode, a, full)
    }

    /** Selects [corner] without touching the zoom, for the selection that follows the playback. */
    fun followCorner(corner: Corner) {
        selectedCorner = corner
    }

    /** The corner lap A is in at the playback clock; null on a straight or when the cursor was set by hand. */
    fun cornerUnderPlayback(corners: List<Corner>): Corner? =
        if (fromPlayback) cornerAt(corners, playbackFrame(a, b, playMs).cursorDistanceM) else null

    /** Moves the playback clock to [ms]; the chart cursor follows lap A. */
    fun seek(ms: Int) {
        playMs = ms.coerceIn(0, endMs)
        fromPlayback = true
        val f = playbackFrame(a, b, playMs)
        cursor = if (mode == AxisMode.TIME) min(playMs, a.lastOrNull()?.timeMs ?: 0) / 1000f else f.cursorDistanceM
    }

    fun togglePlay() {
        if (!playing && playMs >= endMs) seek(0)
        playing = !playing
        if (playing) fromPlayback = true
    }

    /** Map positions of A and B: by own lap time during playback, else at the cursor. */
    fun positions(): Pair<Pair<Float, Float>?, Pair<Float, Float>?> {
        if (fromPlayback) {
            val f = playbackFrame(a, b, playMs)
            return f.a to f.b
        }
        val x = cursor ?: return null to null
        return sampleAt(a, x, mode)?.let { it.x to it.z } to b?.let { l -> sampleAt(l, x, mode)?.let { it.x to it.z } }
    }
}
