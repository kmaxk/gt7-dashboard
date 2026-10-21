package de.stiller.gt7.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.stiller.gt7.session.Corner
import de.stiller.gt7.session.CornerReport
import de.stiller.gt7.session.Debrief
import de.stiller.gt7.storage.ComparisonLap
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the race engineer has to say about the chosen laps. */
sealed interface EngineerState {
    data object Computing : EngineerState
    data object NoTrack : EngineerState
    data object NoComparison : EngineerState
    /** [comparison] is the automatically chosen lap; null when the user picked lap B ([pickedTimeMs]). */
    data class Ready(val debrief: Debrief, val comparison: ComparisonLap?, val pickedTimeMs: Int?) : EngineerState
}

/** The next ([direction] > 0) or previous corner; without a selection the first or last; stops at both ends. */
fun stepCorner(corners: List<Corner>, selected: Corner?, direction: Int): Corner? {
    if (corners.isEmpty()) return null
    val i = corners.indexOf(selected)
    if (i < 0) return if (direction > 0) corners.first() else corners.last()
    return corners[(i + direction.coerceIn(-1, 1)).coerceIn(0, corners.lastIndex)]
}

/** Numbers of the [count] corners that lose the most time; corners without a loss never count. */
fun costliestCorners(reports: List<CornerReport>, count: Int = 3): Set<Int> =
    reports.filter { it.lossMs > 0 }.sortedByDescending { it.lossMs }.take(count).map { it.corner.number }.toSet()

/** Length of the loss bar, 0–1, relative to the largest loss or gain of all corners. */
fun lossBarFraction(report: CornerReport, reports: List<CornerReport>): Float {
    val max = reports.maxOfOrNull { abs(it.lossMs) } ?: 0
    return if (max == 0) 0f else abs(report.lossMs).toFloat() / max
}

/** Selects the next or previous corner; selecting the one already selected would clear it, so that is skipped. */
private fun AnalysisViewport.step(corners: List<Corner>, direction: Int) {
    val next = stepCorner(corners, selectedCorner, direction) ?: return
    if (next != selectedCorner) toggleCorner(next)
}

private fun EngineerState.cornerList(): List<Corner> = (this as? EngineerState.Ready)?.debrief?.reports?.map { it.corner } ?: emptyList()

/** ↓ and → select the next corner, ↑ and ← the previous one, while the element with this modifier has the focus. */
fun Modifier.cornerKeys(state: EngineerState, viewport: AnalysisViewport): Modifier = onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val direction = when (e.key) {
        Key.DirectionDown, Key.DirectionRight -> 1
        Key.DirectionUp, Key.DirectionLeft -> -1
        else -> return@onPreviewKeyEvent false
    }
    viewport.step(state.cornerList(), direction)
    true
}

/** Every corner in driving order with its loss; the selected one shows the tip and its measures. */
@Composable
fun CornerList(state: EngineerState, language: TipLanguage, viewport: AnalysisViewport, modifier: Modifier = Modifier) {
    val compact = LocalCompact.current
    Column(modifier.background(RaceColors.Surface).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RaceLabel("Kurven", Modifier.weight(1f))
            val corners = state.cornerList()
            TextButton(onClick = { viewport.step(corners, -1) }, enabled = corners.isNotEmpty()) { Text("◀") }
            TextButton(onClick = { viewport.step(corners, 1) }, enabled = corners.isNotEmpty()) { Text("▶") }
        }
        val message = when (state) {
            EngineerState.Computing -> EngineerText.computing(language)
            EngineerState.NoTrack -> EngineerText.noTrack(language)
            EngineerState.NoComparison -> EngineerText.noComparison(language)
            is EngineerState.Ready -> when {
                state.debrief.reports.isEmpty() -> EngineerText.noCorners(language)
                state.debrief.tips.isEmpty() -> EngineerText.allGood(language)
                else -> null
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RaceColors.TextMuted, modifier = Modifier.padding(vertical = 4.dp)) }
        val reports = (state as? EngineerState.Ready)?.debrief?.reports ?: return@Column
        val costliest = costliestCorners(reports)
        reports.forEach { r -> CornerRow(r, reports, r.corner.number in costliest, viewport.selectedCorner == r.corner, compact, language) { viewport.toggleCorner(r.corner) } }
    }
}

private fun lossColor(ms: Int) = when {
    ms > 0 -> RaceColors.Loss
    ms < 0 -> RaceColors.Gain
    else -> RaceColors.TextMuted
}

@Composable
private fun CornerRow(
    r: CornerReport, all: List<CornerReport>, costly: Boolean, selected: Boolean, compact: Boolean, language: TipLanguage, onClick: () -> Unit,
) {
    val numbers = digits(13.sp, raceFontFamily()).copy(fontWeight = FontWeight.SemiBold)
    Column(Modifier.fillMaxWidth().background(if (selected) RaceColors.SurfaceRaised else Color.Transparent).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(markerLabel(r.corner.number), style = numbers, modifier = Modifier.width(34.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (costly) RaceColors.Accent else Color.Transparent))
            Spacer(Modifier.width(8.dp))
            if (compact) {
                Text(
                    r.causes.firstOrNull()?.let { EngineerText.cause(it, language) } ?: "–",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f),
                )
            } else {
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(RaceColors.Line)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(lossBarFraction(r, all)).background(lossColor(r.lossMs)))
                }
            }
            Text(
                EngineerText.seconds(r.lossMs, language, signed = true), style = numbers, color = lossColor(r.lossMs),
                textAlign = TextAlign.End, modifier = Modifier.width(72.dp),
            )
        }
        if (selected) {
            Column(Modifier.padding(start = 48.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (r.causes.isNotEmpty() || r.lossMs > 0) Text(EngineerText.tip(r, language), style = MaterialTheme.typography.bodySmall)
                Text(
                    "Bremse ${meterDiff(r.brakeM, r.refBrakeM)} · Scheitel ${r.apexKmh.roundToInt()} / ${r.refApexKmh.roundToInt()} km/h · " +
                        "Gas ${meterDiff(r.throttleM, r.refThrottleM)} · Gang ${r.gear} / ${r.refGear}",
                    style = MaterialTheme.typography.labelSmall, color = RaceColors.TextMuted,
                )
            }
        }
    }
}
