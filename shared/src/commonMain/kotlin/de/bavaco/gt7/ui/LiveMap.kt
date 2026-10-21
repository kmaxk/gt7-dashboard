package de.bavaco.gt7.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bavaco.gt7.session.GapTrend
import de.bavaco.gt7.session.ReferenceInfo
import de.bavaco.gt7.storage.CarCatalog

private val Me = RaceColors.TextPrimary

/** Track [line] (x, z) with the own position [me] and the opponent's [ghost]; both projected onto the line's box. */
@Composable
fun LiveMap(
    line: List<Pair<Float, Float>>,
    me: Pair<Float, Float>?,
    ghost: Pair<Float, Float>?,
    meName: String,
    ghostName: String,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Panel)) {
        Canvas(Modifier.fillMaxSize()) {
            val points = line + listOfNotNull(me, ghost)
            if (points.isEmpty()) return@Canvas
            val projection = MapProjection(points.map { it.first }, points.map { it.second }, size.width, size.height, 24.dp.toPx() * scale)
            if (line.size >= 2) {
                val path = Path()
                line.forEachIndexed { i, (x, z) ->
                    val (px, py) = projection.map(x, z)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, Me.copy(alpha = 0.18f), style = Stroke(10.dp.toPx() * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val radius = 8.dp.toPx() * scale
            ghost?.let { (x, z) ->
                val (px, py) = projection.map(x, z)
                drawCircle(GhostBlue.copy(alpha = 0.25f), radius * 2.2f, Offset(px, py))
                drawCircle(GhostBlue, radius, Offset(px, py))
            }
            me?.let { (x, z) ->
                val (px, py) = projection.map(x, z)
                drawCircle(Me, radius, Offset(px, py))
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(12.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(14.dp * scale),
        ) {
            LegendDot(Me, meName, scale)
            if (ghost != null) LegendDot(GhostBlue, "$ghostName (Geist)", scale)
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String, scale: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp * scale).clip(CircleShape).background(color))
        Text(text, fontSize = 12.sp * scale, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

/** Gap to the opponent, whether it shrinks, and which lap it is measured against. */
@Composable
fun OpponentBox(
    labels: OpponentLabels,
    reference: ReferenceInfo?,
    deltaMs: Int?,
    trend: GapTrend,
    isSelf: Boolean,
    cars: CarCatalog,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(6.dp)).background(Panel).padding(horizontal = 16.dp * scale, vertical = 12.dp * scale),
        verticalArrangement = Arrangement.spacedBy(6.dp * scale),
    ) {
        Label(labels.gapTitle, scale)
        Text(
            formatDelta(deltaMs),
            style = digits(44.sp * scale),
            color = if (deltaMs == null) Color.Gray else if (deltaMs < 0) GoodGreen else BadRed,
        )
        val (trendText, trendColor) = when (trend) {
            GapTrend.CLOSING -> "▲ du holst auf" to GoodGreen
            GapTrend.LOSING -> (if (isSelf) "▼ du verlierst" else "▼ ${labels.opponentName} zieht weg") to BadRed
            GapTrend.STEADY -> " " to Color.Unspecified
        }
        Text(trendText, fontSize = 15.sp * scale, color = trendColor)
        val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        Text(referenceText(reference, labels.opponentName, isSelf, cars), fontSize = 13.sp * scale, color = muted)
    }
}

private fun referenceText(reference: ReferenceInfo?, name: String, isSelf: Boolean, cars: CarCatalog): String {
    if (reference == null) return "Noch keine Runde von ${if (isSelf) "dir" else name} auf dieser Strecke"
    val whose = if (isSelf) "Deine Bestrunde" else "${name}s Bestrunde"
    val car = cars.name(reference.carCode)
    val carText = if (reference.sameCar) "gleiches Auto ($car)" else "anderes Auto: $car"
    return "$whose · $carText · ${formatDateTime(reference.drivenAtMs)}"
}
