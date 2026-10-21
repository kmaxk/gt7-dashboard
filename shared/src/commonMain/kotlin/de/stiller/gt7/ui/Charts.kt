package de.stiller.gt7.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** A polyline of (x, y) points; [dashed] and [steps] (hold value until the next x) affect drawing only. */
data class Series(
    val points: List<Pair<Float, Float>>,
    val color: Color,
    val dashed: Boolean = false,
    val steps: Boolean = false,
)

/** Corner markers (number, x) and the shaded x range of the chosen corner, on the chart's axis. */
data class ChartOverlay(val markers: List<Pair<Int, Float>> = emptyList(), val shade: ClosedFloatingPointRange<Float>? = null)

private val Grid = RaceColors.Line
private val Label = TextStyle(color = RaceColors.TextMuted, fontSize = 10.sp)
private val MarkerLabel = TextStyle(color = RaceColors.TextPrimary.copy(alpha = 0.67f), fontSize = 9.sp)
private val Marker = RaceColors.TextPrimary.copy(alpha = 0.25f)
private val Shade = RaceColors.TextPrimary.copy(alpha = 0.12f)
private val Cursor = RaceColors.TextPrimary.copy(alpha = 0.8f)

private val PlotLeft = 40.dp
private val PlotRight = 8.dp
private val PlotTop = 4.dp
private val PlotBottom = 16.dp

private const val WHEEL_ZOOM = 1.25f
private const val WHEEL_PAN = 0.1f

/**
 * Line chart; with a [viewport] it shows the viewport's x range, its cursor and the [overlay], and
 * pointer input moves the cursor, zooms and pans for all charts at once.
 */
@Composable
fun LineChart(
    title: String,
    series: List<Series>,
    yRange: ClosedFloatingPointRange<Float>? = null,
    modifier: Modifier = Modifier,
    viewport: AnalysisViewport? = null,
    overlay: ChartOverlay = ChartOverlay(),
) {
    val measurer = rememberTextMeasurer()
    Column(modifier) {
        Row {
            Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            if (viewport != null) CursorReadout(series, viewport)
        }
        Box(Modifier.fillMaxWidth().weight(1f).then(if (viewport != null) Modifier.chartGestures(viewport) else Modifier)) {
            Canvas(Modifier.fillMaxSize()) {
                val all = series.flatMap { it.points }
                if (all.isEmpty()) return@Canvas
                val r = viewport?.range
                val x0: Float
                val x1: Float
                val xTicks: List<Float>
                if (r != null) {
                    x0 = r.start; x1 = r.endInclusive
                    xTicks = niceAxis(x0, x1, 6).filter { it in r }
                } else {
                    xTicks = niceAxis(all.minOf { it.first }.coerceAtMost(0f), all.maxOf { it.first }, 6)
                    x0 = xTicks.first(); x1 = xTicks.last()
                }
                val visible = if (r == null) all else all.filter { it.first in r }.ifEmpty { all }
                val yAxis = if (yRange != null) niceAxis(yRange.start, yRange.endInclusive, 4)
                else niceAxis(visible.minOf { it.second }, visible.maxOf { it.second }, 4)
                val xDec = axisDecimals(xTicks); val yDec = axisDecimals(yAxis)
                val plot = plotArea(size)
                val y0 = yAxis.first(); val y1 = yAxis.last()
                fun px(x: Float) = plot.left + (x - x0) / (x1 - x0) * plot.width
                fun py(y: Float) = plot.top + plot.height - (y - y0) / (y1 - y0) * plot.height
                for (y in yAxis) {
                    drawLine(Grid, Offset(plot.left, py(y)), Offset(plot.left + plot.width, py(y)))
                    val t = measurer.measure(formatDecimal(y, yDec), Label)
                    drawText(t, topLeft = Offset(plot.left - t.size.width - 4.dp.toPx(), py(y) - t.size.height / 2f))
                }
                for (x in xTicks) {
                    drawLine(Grid, Offset(px(x), plot.top), Offset(px(x), plot.top + plot.height))
                    val t = measurer.measure(formatDecimal(x, xDec), Label)
                    drawText(t, topLeft = Offset(px(x) - t.size.width / 2f, plot.top + plot.height + 2.dp.toPx()))
                }
                clipRect(plot.left, plot.top, plot.left + plot.width, plot.top + plot.height) {
                    overlay.shade?.let { s -> drawRect(Shade, Offset(px(s.start), plot.top), Size(px(s.endInclusive) - px(s.start), plot.height)) }
                    for ((number, x) in overlay.markers) {
                        if (x < x0 || x > x1) continue
                        drawLine(Marker, Offset(px(x), plot.top), Offset(px(x), plot.top + plot.height))
                        drawText(measurer.measure("$number", MarkerLabel), topLeft = Offset(px(x) + 2.dp.toPx(), plot.top))
                    }
                    for (s in series) {
                        if (s.points.isEmpty()) continue
                        val path = Path()
                        var started = false
                        s.points.forEachIndexed { i, (x, y) ->
                            // skip points well outside the window, keep one on each side so lines reach the edges
                            val next = s.points.getOrNull(i + 1)?.first ?: x
                            val prev = s.points.getOrNull(i - 1)?.first ?: x
                            if (next < x0 || prev > x1) return@forEachIndexed
                            if (!started) { path.moveTo(px(x), py(y)); started = true }
                            else {
                                if (s.steps) path.lineTo(px(x), py(s.points[i - 1].second))
                                path.lineTo(px(x), py(y))
                            }
                        }
                        val effect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                        drawPath(path, s.color, style = Stroke(width = 1.8.dp.toPx(), pathEffect = effect))
                    }
                }
            }
            if (viewport != null) {
                // separate layer: moving the cursor redraws only this line
                Canvas(Modifier.fillMaxSize()) {
                    val c = viewport.cursor ?: return@Canvas
                    val r = viewport.range
                    if (c < r.start || c > r.endInclusive) return@Canvas
                    val plot = plotArea(size)
                    val x = plot.left + (c - r.start) / (r.endInclusive - r.start) * plot.width
                    drawLine(Cursor, Offset(x, plot.top), Offset(x, plot.top + plot.height), 1.dp.toPx())
                }
            }
        }
    }
}

private class Plot(val left: Float, val top: Float, val width: Float, val height: Float)

private fun DrawScope.plotArea(size: Size) = Plot(
    PlotLeft.toPx(), PlotTop.toPx(),
    size.width - PlotLeft.toPx() - PlotRight.toPx(), size.height - PlotTop.toPx() - PlotBottom.toPx(),
)

/** Values of every series at the cursor, each in its series' color. */
@Composable
private fun CursorReadout(series: List<Series>, viewport: AnalysisViewport) {
    val c = viewport.cursor ?: return
    val values = series.map { s -> s to seriesValueAt(s.points, c, s.steps) }.filter { it.second != null }
    if (values.isEmpty()) return
    // small-valued charts (delta, boost) get decimals; steps hold whole values (gear, coasting)
    val decimals = if (series.none { it.steps } && series.all { s -> s.points.all { abs(it.second) < 10f } }) 2 else 0
    val text = buildAnnotatedString {
        values.forEachIndexed { i, (s, v) ->
            if (i > 0) append("  ")
            withStyle(SpanStyle(color = s.color)) { append(formatDecimal(v!!, decimals)) }
        }
    }
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(end = PlotRight))
}

/** x on the viewport's axis under horizontal pixel [px]. */
private fun PointerInputScope.xAt(viewport: AnalysisViewport, px: Float): Float {
    val left = PlotLeft.toPx()
    val width = (size.width - left - PlotRight.toPx()).coerceAtLeast(1f)
    val r = viewport.range
    return r.start + ((px - left) / width).coerceIn(0f, 1f) * (r.endInclusive - r.start)
}

private fun PointerInputScope.plotWidth(): Float = (size.width - PlotLeft.toPx() - PlotRight.toPx()).coerceAtLeast(1f)

/**
 * Mouse hover, tap and horizontal drag move the cursor; Ctrl/⌘ + wheel zooms, Shift + wheel or a sideways
 * wheel pans (the plain wheel keeps scrolling the page); two fingers pinch to zoom and move to pan.
 */
private fun Modifier.chartGestures(viewport: AnalysisViewport): Modifier = this
    .pointerInput(viewport) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                val ch = e.changes.firstOrNull() ?: continue
                when (e.type) {
                    PointerEventType.Move -> if (!ch.pressed && ch.type == PointerType.Mouse) viewport.moveCursor(xAt(viewport, ch.position.x))
                    PointerEventType.Scroll -> {
                        val d = ch.scrollDelta
                        val span = viewport.range.endInclusive - viewport.range.start
                        when {
                            e.keyboardModifiers.isCtrlPressed || e.keyboardModifiers.isMetaPressed -> {
                                viewport.zoom(if (d.y > 0) WHEEL_ZOOM else 1f / WHEEL_ZOOM, xAt(viewport, ch.position.x))
                                ch.consume()
                            }
                            e.keyboardModifiers.isShiftPressed || abs(d.x) > abs(d.y) -> {
                                val amount = if (abs(d.x) > abs(d.y)) d.x else d.y
                                viewport.pan(amount * span * WHEEL_PAN)
                                ch.consume()
                            }
                        }
                    }
                }
            }
        }
    }
    .pointerInput(viewport) { detectTapGestures { viewport.moveCursor(xAt(viewport, it.x)) } }
    .pointerInput(viewport) { detectHorizontalDragGestures { change, _ -> viewport.moveCursor(xAt(viewport, change.position.x)) } }
    // innermost, so it sees two-finger gestures before the drag detector does
    .pointerInput(viewport) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val e = awaitPointerEvent()
                if (e.changes.count { it.pressed } >= 2) {
                    val zoom = e.calculateZoom()
                    val pan = e.calculatePan()
                    val span = viewport.range.endInclusive - viewport.range.start
                    if (zoom != 1f) viewport.zoom(1f / zoom, xAt(viewport, e.calculateCentroid().x))
                    if (pan.x != 0f) viewport.pan(-pan.x / plotWidth() * span)
                    e.changes.forEach { it.consume() }
                }
            } while (e.changes.any { it.pressed })
        }
    }
