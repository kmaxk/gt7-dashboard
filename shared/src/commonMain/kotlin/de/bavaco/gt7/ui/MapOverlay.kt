package de.bavaco.gt7.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bavaco.gt7.session.Corner
import de.bavaco.gt7.session.LapSample

private val Zone = RaceColors.TextPrimary.copy(alpha = 0.35f)
private val CornerNumber = TextStyle(color = RaceColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
private val SelectedNumber = CornerNumber.copy(color = RaceColors.Carbon)
private val DotRing = RaceColors.Carbon

/** The chosen corner's zone under lap A; drawn before the lap line so the line stays on top. */
fun DrawScope.drawCornerZone(projection: MapProjection, a: List<LapSample>, corner: Corner?) {
    if (corner == null) return
    val zone = a.filter { it.distanceM >= corner.startM && it.distanceM <= corner.endM }
    if (zone.size < 2) return
    val path = Path()
    zone.forEachIndexed { i, s ->
        val (x, y) = projection.map(s.x, s.z)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Zone, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Corner numbers beside lap A's apexes, and the dots of A and B at the cursor or during playback. */
fun DrawScope.drawMapMarks(projection: MapProjection, a: List<LapSample>, corners: List<Corner>, viewport: AnalysisViewport?, measurer: TextMeasurer) {
    val radius = 8.dp.toPx()
    for (c in corners) {
        val s = sampleAt(a, c.apexM, AxisMode.DISTANCE) ?: continue
        val (x, y) = projection.map(s.x, s.z)
        val selected = viewport?.selectedCorner == c
        val t = measurer.measure(markerLabel(c.number), if (selected) SelectedNumber else CornerNumber)
        // wide enough for two-digit turns
        val r = maxOf(radius, t.size.width / 2f + 3.dp.toPx())
        val center = Offset(x + r * 1.4f, y - r * 1.4f)
        drawCircle(if (selected) RaceColors.Accent else RaceColors.SurfaceRaised.copy(alpha = 0.85f), r, center)
        drawText(t, topLeft = Offset(center.x - t.size.width / 2f, center.y - t.size.height / 2f))
    }
    if (viewport == null) return
    val (pa, pb) = viewport.positions()
    fun dot(p: Pair<Float, Float>?, color: Color) {
        if (p == null) return
        val (x, y) = projection.map(p.first, p.second)
        drawCircle(DotRing, 6.dp.toPx(), Offset(x, y))
        drawCircle(color, 4.5.dp.toPx(), Offset(x, y))
    }
    dot(pb, LapBColor)
    dot(pa, LapAColor)
}
