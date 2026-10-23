package de.bavaco.gt7.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bavaco.gt7.session.isShiftPoint
import de.bavaco.gt7.session.isSlipAlert

/** Reference size of the live view at scale 1.0. */
private const val REF_WIDTH_DP = 1000f
private const val REF_HEIGHT_DP = 640f
private const val MAX_SCALE = 2.5f
private const val MIN_COMPACT_SCALE = 0.45f

/** Factor all live-view sizes are multiplied with, so the dashboard fills large screens and, when [compact], fits a phone. */
fun dashboardScale(widthDp: Float, heightDp: Float, compact: Boolean = false): Float =
    minOf(widthDp / REF_WIDTH_DP, heightDp / REF_HEIGHT_DP).coerceIn(if (compact) MIN_COMPACT_SCALE else 1f, MAX_SCALE)

/** 1 <-> 0 pulsing value for blinking elements. */
@Composable
fun blinkAlpha(): State<Float> =
    rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(250), RepeatMode.Reverse),
        label = "blinkAlpha",
    )

@Composable
fun HBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 24.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(4.dp)).background(Panel)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

/** Throttle (filled) and brake (on top) over the trace window; the right edge is now. */
@Composable
fun PedalTraceChart(points: List<PedalPoint>, windowPackets: Int, scale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(4.dp)).background(Panel)) {
        val mid = size.height / 2
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        if (points.size < 2) return@Canvas
        val start = points.last().packetId - windowPackets + 1
        fun x(p: PedalPoint) = (p.packetId - start).toFloat() / (windowPackets - 1) * size.width
        fun y(v: Float) = size.height * (1f - v.coerceIn(0f, 1f))
        fun line(value: (PedalPoint) -> Float) = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p), y(value(p))) else lineTo(x(p), y(value(p))) }
        }
        val throttle = line { it.throttle }
        val area = Path().apply {
            addPath(throttle)
            lineTo(x(points.last()), size.height)
            lineTo(x(points.first()), size.height)
            close()
        }
        drawPath(area, GoodGreen.copy(alpha = 0.18f))
        drawPath(throttle, GoodGreen, style = Stroke(2.dp.toPx() * scale))
        // brake only where pressed, so no red line runs along the bottom
        val brake = Path().apply {
            var pen = false
            points.forEachIndexed { i, p ->
                val pressed = p.brake > 0f || (points.getOrNull(i - 1)?.brake ?: 0f) > 0f || (points.getOrNull(i + 1)?.brake ?: 0f) > 0f
                if (pressed && pen) lineTo(x(p), y(p.brake)) else if (pressed) moveTo(x(p), y(p.brake))
                pen = pressed
            }
        }
        drawPath(brake, BadRed, style = Stroke(2.5.dp.toPx() * scale))
    }
}

@Composable
fun Label(text: String, scale: Float = 1f, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) =
    Text(text, fontSize = 13.sp * scale, color = color, softWrap = false, maxLines = 1)

@Composable
fun RpmBar(rpm: Float, alertMin: Int, alertMax: Int, height: Dp) {
    val blink = blinkAlpha().value
    val limiter = isShiftPoint(rpm, alertMax)
    val color = when {
        limiter -> BadRed
        alertMin > 0 && rpm >= alertMin -> WarnAmber
        else -> GoodGreen
    }
    val top = if (alertMax > 0) alertMax * 1.05f else 9000f
    HBar(rpm / top, if (limiter) color.copy(alpha = blink) else color, Modifier, height)
}

/** Blinking frame around the whole dashboard at GT7's shift point; nothing drawn below it. */
@Composable
fun ShiftFrame(active: Boolean, width: Dp, modifier: Modifier = Modifier) {
    if (!active) return
    val blink = blinkAlpha().value
    Box(modifier.border(width, BadRed.copy(alpha = blink)))
}

@Composable
fun SlipDot(slip: Float?, scale: Float) {
    val blink = blinkAlpha().value
    val alert = isSlipAlert(slip)
    val color = if (alert) BadRed.copy(alpha = blink) else if (slip == null) Panel else GoodGreen.copy(alpha = 0.5f)
    Box(Modifier.size(16.dp * scale).clip(CircleShape).background(color))
}

@Composable
fun TireCell(tempC: Float, slip: Float?, scale: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        SlipDot(slip, scale)
        Text("${tempC.toInt()}°", style = digits(36.sp * scale))
    }
}
