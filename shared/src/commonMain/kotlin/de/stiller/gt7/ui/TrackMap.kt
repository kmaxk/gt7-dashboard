package de.stiller.gt7.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.stiller.gt7.session.Corner
import de.stiller.gt7.session.LapSample

private val Neutral = RaceColors.TextMuted

/**
 * Lap path; segments green where the lap gains on the reference, red where it loses (plain gray if [delta] is empty).
 * With a [viewport]: corner numbers, the chosen corner and the dots of A and B.
 */
@Composable
fun TrackMap(
    lap: List<LapSample>,
    delta: List<Pair<Float, Int>>,
    modifier: Modifier = Modifier,
    viewport: AnalysisViewport? = null,
    corners: List<Corner> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        if (lap.size < 2) return@Canvas
        val projection = MapProjection(lap.map { it.x }, lap.map { it.z }, size.width, size.height, 16.dp.toPx())
        val pts = lap.map { projection.map(it.x, it.z) }
        drawCornerZone(projection, lap, viewport?.selectedCorner)
        val mids = (1 until lap.size).map { (lap[it - 1].distanceM + lap[it].distanceM) / 2f }
        val trends = segmentTrends(delta, mids)
        val stroke = 4.dp.toPx()
        for (i in 1 until pts.size) {
            val color = when (trends[i - 1]) {
                Trend.GAIN -> GoodGreen
                Trend.LOSS -> BadRed
                Trend.NEUTRAL -> Neutral
            }
            drawLine(color, Offset(pts[i - 1].first, pts[i - 1].second), Offset(pts[i].first, pts[i].second), stroke, StrokeCap.Round)
        }
        drawMapMarks(projection, lap, corners, viewport, measurer)
    }
}
