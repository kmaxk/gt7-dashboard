package de.bavaco.gt7.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.bavaco.gt7.storage.LapSummary
import de.bavaco.gt7.storage.SessionSummary
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val Selected = RaceColors.SurfaceRaised

private fun two(n: Int) = n.toString().padStart(2, '0')

/** `dd.MM.yyyy HH:mm` in [zone]. */
fun formatDateTime(epochMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
    return "${two(t.day)}.${two(t.month.ordinal + 1)}.${t.year} ${two(t.hour)}:${two(t.minute)}"
}

/**
 * Selection rule: [picked] holds up to two laps (A first). Clicking a picked lap removes it; a third lap
 * is ignored; B must come from the same session as A or, if A's session has a track, from the same track.
 */
fun toggleLap(picked: List<LapSummary>, lap: LapSummary, sessions: List<SessionSummary>): List<LapSummary> {
    if (picked.any { it.id == lap.id }) return picked.filter { it.id != lap.id }
    if (picked.size >= 2) return picked
    val a = picked.firstOrNull() ?: return listOf(lap)
    val sa = sessions.firstOrNull { it.id == a.sessionId }
    val sl = sessions.firstOrNull { it.id == lap.sessionId }
    val compatible = lap.sessionId == a.sessionId || (sa?.trackId != null && sa.trackId == sl?.trackId)
    return if (compatible) picked + lap else picked
}

/** Laps of the open session; [onStar] (null = session without track) marks or unmarks the driver's reference lap. */
@Composable
fun LapList(
    laps: List<LapSummary>, bestMs: Int?, picked: List<LapSummary>, onToggle: (LapSummary) -> Unit,
    modifier: Modifier = Modifier, onStar: ((LapSummary) -> Unit)? = null,
) {
    Column(modifier) {
        Text("Runden", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
        LazyColumn {
            items(laps, key = { it.id }) { lap ->
                val idx = picked.indexOfFirst { it.id == lap.id }
                val tint = if (lap.isValid) Color.Unspecified else Color.Gray
                Row(
                    Modifier.fillMaxWidth().background(if (idx >= 0) Selected else Color.Transparent)
                        .clickable { onToggle(lap) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (idx == 0) "A" else if (idx == 1) "B" else " ",
                        color = if (idx == 0) LapAColor else LapBColor, fontFamily = FontFamily.Monospace,
                    )
                    if (onStar != null) {
                        Text(
                            if (lap.isReference) "★" else "☆",
                            color = if (lap.isReference) WarnAmber else tint,
                            modifier = Modifier.clickable(enabled = lap.isValid || lap.isReference) { onStar(lap) },
                        )
                    }
                    Text("${lap.number}", color = tint, modifier = Modifier.padding(end = 4.dp))
                    Text(formatLapTime(lap.timeMs), color = tint, fontFamily = FontFamily.Monospace)
                    Text(
                        if (bestMs != null) formatDelta(lap.timeMs - bestMs) else "–",
                        color = tint, fontFamily = FontFamily.Monospace,
                    )
                    Text("${formatDecimal(lap.fuelUsed, 1)} %", color = tint)
                    if (lap.isReference) Text("REF", color = WarnAmber, style = MaterialTheme.typography.labelSmall)
                    if (!lap.isValid) Text("ungültig", color = tint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
