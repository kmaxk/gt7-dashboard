package de.bavaco.gt7.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bavaco.gt7.session.Corner
import de.bavaco.gt7.session.InputShares
import de.bavaco.gt7.session.LapSample
import de.bavaco.gt7.session.coasting
import de.bavaco.gt7.session.deltaCurve
import de.bavaco.gt7.session.inputShares
import de.bavaco.gt7.storage.LapSummary
import de.bavaco.gt7.storage.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MainChartHeight = 180.dp
private val PedalChartHeight = 150.dp
private val MoreChartHeight = 120.dp

/** Lap A, the picked lap B (if any) and the sessions they come from, for the header. */
class CockpitLaps(val lapA: LapSummary, val sessionA: SessionSummary?, val pickedB: LapSummary?, val sessionB: SessionSummary?)

/**
 * The analysis cockpit: header cards, map with the corner list, the main charts (delta, speed, throttle/brake),
 * further channels on request, and the open session's laps. ↑↓←→ step through the corners while it has the focus.
 * [moreChannels] / [onMoreChannels] keep the "further channels" state above lap changes.
 */
@Composable
fun CockpitAnalysis(
    viewport: AnalysisViewport,
    engineer: EngineerState,
    language: TipLanguage,
    picked: CockpitLaps,
    laps: List<LapSummary>,
    loadSamples: (Long) -> List<LapSample>,
    loadLaps: (Long) -> List<LapSummary>,
    carName: (Int) -> String,
    moreChannels: Boolean,
    onMoreChannels: (Boolean) -> Unit,
    toolbar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = LocalCompact.current
    val corners = (engineer as? EngineerState.Ready)?.debrief?.reports?.map { it.corner } ?: emptyList()
    var shares by remember { mutableStateOf<Map<Long, InputShares>>(emptyMap()) }
    var stats by remember { mutableStateOf<LapTableStats?>(null) }
    // the header's theoretical best belongs to lap A's session, which need not be the open one
    var statsA by remember { mutableStateOf<LapTableStats?>(null) }
    LaunchedEffect(laps, picked.lapA.sessionId) {
        stats = null
        statsA = null
        withContext(Dispatchers.Default) {
            val samples = laps.associate { it.id to loadSamples(it.id) }
            shares = samples.mapValues { inputShares(it.value) }
            stats = lapTableStats(laps, samples)
            val lapsA = sessionLapsOfA(picked.lapA, laps, loadLaps)
            statsA = if (lapsA === laps) stats else lapTableStats(lapsA, lapsA.associate { it.id to loadSamples(it.id) })
        }
    }
    val header = cockpitHeader(picked.lapA, picked.sessionA, picked.pickedB, picked.sessionB, engineer, statsA, carName)
    val focus = remember { FocusRequester() }
    LaunchedEffect(viewport) { runCatching { focus.requestFocus() } }
    // the list and the map follow the replay corner by corner; a straight keeps the last corner open
    val replayCorner = viewport.cornerUnderPlayback(corners)
    LaunchedEffect(replayCorner) { replayCorner?.let(viewport::followCorner) }

    BoxWithConstraints(modifier) {
    val mapRow = mapRowHeight(maxHeight.value).dp
    Column(
        Modifier.fillMaxSize().focusRequester(focus).focusable().cornerKeys(engineer, viewport).verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { toolbar() }
        HeaderCards(header, compact)
        val map: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                RaceLine(viewport, corners, Modifier.fillMaxWidth().weight(1f))
                PlaybackBar(viewport, Modifier.fillMaxWidth())
            }
        }
        if (compact) {
            map(Modifier.fillMaxWidth().aspectRatio(1.2f))
            CornerList(engineer, language, viewport, Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth().height(mapRow), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                map(Modifier.weight(0.6f).fillMaxHeight())
                CornerList(engineer, language, viewport, Modifier.weight(0.4f).fillMaxHeight(), scrollable = true)
            }
        }
        Charts(viewport, corners, moreChannels, onMoreChannels)
        SessionLaps(laps, shares, stats, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    }
}

@Composable
private fun HeaderCards(cards: List<HeaderCard>, compact: Boolean) {
    @Composable
    fun card(c: HeaderCard, m: Modifier) {
        RaceCard(m, accent = toneColor(c.tone).takeIf { c.tone != Tone.Neutral } ?: RaceColors.Line) {
            RaceLabel(c.label)
            RaceNumber(c.value, toneColor(c.tone), size = if (compact) 20.sp else 26.sp)
            Text(c.detail ?: " ", style = MaterialTheme.typography.labelSmall, color = RaceColors.TextMuted, maxLines = 1)
        }
    }
    val rows = if (compact) cards.chunked(2) else listOf(cards)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { card(it, Modifier.weight(1f)) }
            }
        }
    }
}

private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.LapA -> RaceColors.LapA
    Tone.LapB -> RaceColors.LapB
    Tone.Loss -> RaceColors.Loss
    Tone.Gain -> RaceColors.Gain
    Tone.Best -> RaceColors.Best
    Tone.Neutral -> RaceColors.TextPrimary
}

/** Delta, speed and throttle/brake always; gear, revs, boost and coasting folded under "Weitere Kanäle". */
@Composable
private fun Charts(viewport: AnalysisViewport, corners: List<Corner>, more: Boolean, onMore: (Boolean) -> Unit) {
    val a = viewport.a
    val b = viewport.b
    val mode = viewport.mode
    val overlay = chartOverlay(corners, viewport.selectedCorner, mode, a)
    val delta = remember(a, b) { if (b != null) deltaCurve(a, b) else emptyList() }
    @Composable
    fun chart(title: String, height: androidx.compose.ui.unit.Dp, yRange: ClosedFloatingPointRange<Float>? = null, series: () -> List<Series>) {
        val s = remember(a, b, mode, title) { series() }
        LineChart(title, s, yRange, Modifier.fillMaxWidth().height(height), viewport, overlay)
    }
    if (b != null) chart("Zeitdifferenz A − B (s, positiv = A langsamer)", MainChartHeight) { listOf(Series(deltaSeries(delta, mode, a), RaceColors.LapA)) }
    chart("Tempo (km/h)", MainChartHeight) { lapSeries(a, b, mode) { it.speedMps * 3.6f } }
    chart("Gas / Bremse (%)", PedalChartHeight, 0f..100f) { throttleBrake(a, b, mode) }
    TextButton(onClick = { onMore(!more) }) {
        RaceIcon(if (more) RaceIconKind.Expand else RaceIconKind.Collapse)
        Spacer(Modifier.width(6.dp))
        Text("Weitere Kanäle")
    }
    if (more) {
        chart("Gang", MoreChartHeight) { lapSeries(a, b, mode, steps = true) { it.gear.toFloat() } }
        chart("Drehzahl (U/min)", MoreChartHeight) { lapSeries(a, b, mode) { it.rpm } }
        chart("Ladedruck (bar)", MoreChartHeight) { lapSeries(a, b, mode) { it.boostBar } }
        chart("Rollen (weder Gas noch Bremse)", MoreChartHeight, 0f..1f) { lapSeries(a, b, mode, steps = true) { coasting(it) } }
    }
}

private fun lapSeries(a: List<LapSample>, b: List<LapSample>?, mode: AxisMode, steps: Boolean = false, f: (LapSample) -> Float): List<Series> =
    listOfNotNull(
        Series(a.map { xOf(it, mode) to f(it) }, RaceColors.LapA, steps = steps),
        b?.let { Series(it.map { s -> xOf(s, mode) to f(s) }, RaceColors.LapB, steps = steps) },
    )

/** Throttle green and brake red; lap A solid, lap B dashed. */
private fun throttleBrake(a: List<LapSample>, b: List<LapSample>?, mode: AxisMode): List<Series> {
    fun pair(l: List<LapSample>, dashed: Boolean) = listOf(
        Series(l.map { xOf(it, mode) to it.throttle * 100f }, RaceColors.Gain, dashed),
        Series(l.map { xOf(it, mode) to it.brake * 100f }, RaceColors.Loss, dashed),
    )
    return pair(a, false) + (b?.let { pair(it, true) } ?: emptyList())
}

/** Lap A thin on top of a wider, translucent lap B, both in one projection so they line up; corners and dots on top. */
@Composable
private fun RaceLine(viewport: AnalysisViewport, corners: List<Corner>, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val a = viewport.a
    val b = viewport.b
    Canvas(modifier) {
        val all = a + (b ?: emptyList())
        if (all.size < 2) return@Canvas
        val projection = MapProjection(all.map { it.x }, all.map { it.z }, size.width, size.height, 18.dp.toPx())
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
        b?.let { line(it, RaceColors.LapB.copy(alpha = 0.45f), 6.dp.toPx()) }
        line(a, RaceColors.LapA, 2.dp.toPx())
        a.firstOrNull()?.let { s ->
            val (px, py) = projection.map(s.x, s.z)
            drawCircle(RaceColors.TextPrimary, 4.dp.toPx(), Offset(px, py))
        }
        drawMapMarks(projection, a, corners, viewport, measurer)
    }
}
