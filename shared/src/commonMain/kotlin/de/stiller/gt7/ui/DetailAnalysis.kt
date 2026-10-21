package de.stiller.gt7.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.stiller.gt7.session.Corner
import de.stiller.gt7.session.InputShares
import de.stiller.gt7.session.LapSample
import de.stiller.gt7.session.PeaksAndValleys
import de.stiller.gt7.session.coasting
import de.stiller.gt7.session.deltaCurve
import de.stiller.gt7.session.inputShares
import de.stiller.gt7.session.peaksAndValleys
import de.stiller.gt7.storage.LapSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val ChartHeight = 140.dp

/**
 * Detail analysis after snipem's gt7dashboard ("Get Faster"): stacked charts on the left, all following the
 * [viewport]; on the right the race engineer ([panel]), race line with playback, lap-time chart, speed
 * peaks/valleys and the lap table of the open session.
 */
@Composable
fun DetailAnalysisView(
    viewport: AnalysisViewport,
    corners: List<Corner>,
    laps: List<LapSummary>,
    loadSamples: (Long) -> List<LapSample>,
    modifier: Modifier = Modifier,
    panel: (@Composable () -> Unit)? = null,
) {
    val a = viewport.a
    val b = viewport.b
    val mode = viewport.mode
    val delta = remember(a, b) { if (b != null) deltaCurve(a, b) else emptyList() }
    val pvA = remember(a) { peaksAndValleys(a) }
    val pvB = remember(b) { b?.let { peaksAndValleys(it) } }
    var shares by remember { mutableStateOf<Map<Long, InputShares>>(emptyMap()) }
    var stats by remember { mutableStateOf<LapTableStats?>(null) }
    LaunchedEffect(laps) {
        withContext(Dispatchers.Default) {
            val samples = laps.associate { it.id to loadSamples(it.id) }
            shares = samples.mapValues { inputShares(it.value) }
            stats = lapTableStats(laps, samples)
        }
    }
    val overlay = chartOverlay(corners, viewport.selectedCorner, mode, a)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(0.65f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val chart = Modifier.fillMaxWidth().height(ChartHeight)
            @Composable
            fun lapChart(title: String, yRange: ClosedFloatingPointRange<Float>? = null, steps: Boolean = false, f: (LapSample) -> Float) {
                val s = remember(a, b, mode) { series(a, b, mode, steps, f) }
                LineChart(title, s, yRange, chart, viewport, overlay)
            }
            if (b != null) {
                val d = remember(delta, mode) { listOf(Series(deltaSeries(delta, mode, a), LapAColor)) }
                LineChart("Zeitdifferenz A − B (s, positiv = A langsamer)", d, modifier = chart, viewport = viewport, overlay = overlay)
            }
            lapChart("Tempo (km/h)") { it.speedMps * 3.6f }
            lapChart("Gas (%)", 0f..100f) { it.throttle * 100f }
            lapChart("Bremse (%)", 0f..100f) { it.brake * 100f }
            lapChart("Rollen (weder Gas noch Bremse)", 0f..1f, steps = true) { coasting(it) }
            lapChart("Gang", steps = true) { it.gear.toFloat() }
            lapChart("Drehzahl (U/min)") { it.rpm }
            lapChart("Ladedruck (bar)") { it.boostBar }
        }
        Column(Modifier.weight(0.35f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            panel?.invoke()
            Text("Ideallinie", style = MaterialTheme.typography.labelMedium)
            RaceLine(viewport, corners, Modifier.fillMaxWidth().aspectRatio(1f))
            PlaybackBar(viewport, Modifier.fillMaxWidth())
            LapTimeChart(laps, Modifier.fillMaxWidth().height(LapTimeChartHeight))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PeakList("Runde A", pvA, LapAColor, Modifier.weight(1f))
                pvB?.let { PeakList("Runde B", it, LapBColor, Modifier.weight(1f)) }
            }
            LapTable(laps, shares, stats)
        }
    }
}

private val LapTimeChartHeight = 120.dp

/** Valid lap times of the open session over the lap number; hidden with fewer than two. */
@Composable
private fun LapTimeChart(laps: List<LapSummary>, modifier: Modifier) {
    val valid = laps.filter { it.isValid }
    if (valid.size < 2) return
    val series = remember(laps) { listOf(Series(valid.map { it.number.toFloat() to it.timeMs / 1000f }, LapAColor)) }
    LineChart("Rundenzeiten (gültige Runden, s)", series, modifier = modifier)
}

private fun series(a: List<LapSample>, b: List<LapSample>?, mode: AxisMode, steps: Boolean = false, f: (LapSample) -> Float): List<Series> =
    listOfNotNull(
        Series(a.map { xOf(it, mode) to f(it) }, LapAColor, steps = steps),
        b?.let { Series(it.map { s -> xOf(s, mode) to f(s) }, LapBColor, steps = steps) },
    )

/** Lap A thin on top of a wider, translucent lap B, both in one projection so they line up; corners and dots on top. */
@Composable
private fun RaceLine(viewport: AnalysisViewport, corners: List<Corner>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val a = viewport.a
    val b = viewport.b
    Canvas(modifier) {
        val all = a + (b ?: emptyList())
        if (all.size < 2) return@Canvas
        val projection = MapProjection(all.map { it.x }, all.map { it.z }, size.width, size.height, 16.dp.toPx())
        fun line(samples: List<LapSample>, color: Color, width: Float) {
            if (samples.size < 2) return
            val path = Path()
            samples.forEachIndexed { i, s ->
                val (px, py) = projection.map(s.x, s.z)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCornerZone(projection, a, viewport.selectedCorner)
        b?.let { line(it, LapBColor.copy(alpha = 0.5f), 6.dp.toPx()) }
        line(a, LapAColor, 2.dp.toPx())
        a.firstOrNull()?.let { s ->
            val (px, py) = projection.map(s.x, s.z)
            drawCircle(Color.White, 4.dp.toPx(), Offset(px, py))
        }
        drawMapMarks(projection, a, corners, viewport, measurer)
    }
}

@Composable
private fun PeakList(title: String, pv: PeaksAndValleys, color: Color, modifier: Modifier) {
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = color)
        Text("Spitzen", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        pv.peaks.forEachIndexed { i, p -> Text("${i + 1}. ${kmh(p.speedMps)} km/h · ${p.distanceM.roundToInt()} m", style = mono) }
        Text("Täler", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        pv.valleys.forEachIndexed { i, p -> Text("${i + 1}. ${kmh(p.speedMps)} km/h · ${p.distanceM.roundToInt()} m", style = mono) }
    }
}

private fun kmh(mps: Float) = (mps * 3.6f).roundToInt()

private val BestSector = RaceColors.Best

/** Laps of the open session: time, gap to its best valid lap, Vmax, sectors (best purple), fuel and pedal shares. */
@Composable
private fun LapTable(laps: List<LapSummary>, shares: Map<Long, InputShares>, stats: LapTableStats?) {
    if (laps.isEmpty()) return
    val best = laps.filter { it.isValid }.minOfOrNull { it.timeMs }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val head = MaterialTheme.typography.labelSmall
    val widths = listOf(24.dp, 70.dp, 62.dp, 40.dp, 56.dp, 56.dp, 56.dp, 52.dp, 46.dp, 52.dp, 48.dp)
    Column {
        Text("Runden der Sitzung", style = MaterialTheme.typography.labelMedium)
        // wider than the side column: the rows scroll sideways
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            TableRow(listOf("#", "Zeit", "Diff", "Vmax", "S1", "S2", "S3", "Sprit", "Gas", "Bremse", "Rollen"), widths, head, { Color.Unspecified })
            laps.forEach { lap ->
                val s = shares[lap.id]
                val sectors = stats?.sectors?.get(lap.id)
                val base = if (lap.isValid) Color.Unspecified else Color.Gray
                TableRow(
                    listOf(
                        "${lap.number}",
                        formatLapTime(lap.timeMs),
                        if (best != null && lap.isValid) formatDelta(lap.timeMs - best) else "–",
                        stats?.vmaxKmh?.get(lap.id)?.let { "${it.roundToInt()}" } ?: "…",
                    ) + (0 until 3).map { i -> sectors?.getOrNull(i)?.let { formatDecimal(it / 1000f, 3) } ?: "–" } + listOf(
                        formatDecimal(lap.fuelUsed, 1),
                        percent(s?.fullThrottle),
                        percent(s?.fullBrake),
                        percent(s?.coasting),
                    ),
                    widths, mono,
                    { i ->
                        val sector = i - 4
                        if (lap.isValid && sector in 0..2 && sectors != null && stats.bestSectors?.getOrNull(sector) == sectors[sector]) BestSector else base
                    },
                )
            }
        }
        stats?.theoreticalMs?.let { t ->
            val gap = best?.let { " (${formatDelta(t - it)} zur Bestzeit)" } ?: ""
            Text("Theoretische Bestzeit: ${formatLapTime(t)}$gap", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            "Sektoren = Drittel der schnellsten Runde, beste violett; ungültige Runden grau. Gas/Bremse = Anteil Vollgas/Vollbremsung, Rollen = ohne Pedal; Sprit in %",
            style = MaterialTheme.typography.labelSmall, color = Color.Gray,
        )
    }
}

private fun percent(share: Float?) = share?.let { "${(it * 100f).roundToInt()} %" } ?: "…"

@Composable
private fun TableRow(cells: List<String>, widths: List<Dp>, style: TextStyle, color: (Int) -> Color) {
    Row(Modifier.padding(vertical = 2.dp)) {
        cells.forEachIndexed { i, c -> Text(c, style = style, color = color(i), softWrap = false, maxLines = 1, modifier = Modifier.width(widths[i])) }
    }
}
