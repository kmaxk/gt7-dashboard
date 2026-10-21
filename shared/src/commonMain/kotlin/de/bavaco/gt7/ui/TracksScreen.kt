package de.bavaco.gt7.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.bavaco.gt7.session.LapSample
import de.bavaco.gt7.session.SessionController
import de.bavaco.gt7.storage.LineCandidate
import de.bavaco.gt7.storage.SessionRepository
import de.bavaco.gt7.storage.TrackSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A track row with everything the tab shows: summary, line samples and the laps that can become the line. */
private data class TrackRow(val summary: TrackSummary, val line: List<LapSample>, val candidates: List<LineCandidate>)

/** Tracks tab: recorded tracks with map preview; rename, delete (laps stay) and choose the lap drawn as line. */
@Composable
fun TracksScreen(controller: SessionController, repo: SessionRepository) {
    val version by controller.tracksVersion.collectAsState()
    // laps driven meanwhile change counts and the automatic line
    val sessions by remember { repo.sessions() }.collectAsState(emptyList())
    var rows by remember { mutableStateOf<List<TrackRow>?>(null) }
    var deleting by remember { mutableStateOf<TrackSummary?>(null) }
    LaunchedEffect(version, sessions) {
        rows = withContext(Dispatchers.Default) {
            repo.trackSummaries().map { TrackRow(it, repo.trackLine(it.id) ?: emptyList(), repo.lineCandidates(it.id)) }
        }
    }
    val list = rows
    when {
        list == null -> Unit
        list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Noch keine Strecke aufgezeichnet. Nach der ersten Runde fragt das Dashboard nach dem Namen.")
        }
        else -> LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.summary.id }) { row ->
                TrackCard(row, onRename = { controller.renameTrack(row.summary.id, it) }, onDelete = { deleting = row.summary }) {
                    controller.setTrackLine(row.summary.id, it)
                }
            }
        }
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Strecke löschen?") },
            text = {
                Text("„${t.name}“ wird gelöscht. Die ${t.lapCount} Runden darauf bleiben in der Analyse, aber ohne Strecke und nicht mehr in der Bestenliste. Bei der nächsten Fahrt wird die Strecke neu abgefragt.")
            },
            confirmButton = {
                TextButton(onClick = { controller.deleteTrack(t.id); deleting = null }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun TrackCard(row: TrackRow, onRename: (String) -> Unit, onDelete: () -> Unit, onLine: (Long?) -> Unit) {
    val t = row.summary
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(t.name) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Panel).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinePreview(row.line, Modifier.size(140.dp, 100.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, isError = name.isBlank())
                    Button(shape = RaceButtonShape, onClick = { onRename(name); editing = false }, enabled = name.isNotBlank()) { Text("Speichern") }
                    TextButton(onClick = { editing = false; name = t.name }) { Text("Abbrechen") }
                }
            } else {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
            }
            Text(trackFacts(t, row.line), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            LinePicker(t.lineLapId, row.candidates, onLine)
        }
        if (!editing) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(shape = RaceButtonShape, onClick = { editing = true; name = t.name }) { Text("Umbenennen") }
                OutlinedButton(shape = RaceButtonShape, onClick = onDelete) { Text("Löschen") }
            }
        }
    }
}

/** `2.40 km · 12 Runden · zuletzt 17.10.2026 12:30`; length from the line, omitted without one. */
private fun trackFacts(t: TrackSummary, line: List<LapSample>): String {
    val parts = mutableListOf<String>()
    if (line.size >= 2) parts += "${formatDecimal((line.last().distanceM - line.first().distanceM) / 1000f, 2)} km"
    parts += if (t.lapCount == 1) "1 Runde" else "${t.lapCount} Runden"
    t.lastDrivenAtMs?.let { parts += "zuletzt ${formatDateTime(it)}" }
    return parts.joinToString(" · ")
}

@Composable
private fun LinePicker(selected: Long?, candidates: List<LineCandidate>, onLine: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = candidates.find { it.lapId == selected }
    val label = if (current == null) "Schnellste Runde (automatisch)" else candidateText(current)
    Box {
        TextButton(onClick = { open = true }, enabled = candidates.isNotEmpty()) {
            Text("Streckenlinie: $label")
            Spacer(Modifier.width(6.dp))
            RaceIcon(RaceIconKind.Expand, size = 8.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Schnellste Runde (automatisch)") }, onClick = { onLine(null); open = false })
            candidates.forEach { c -> DropdownMenuItem(text = { Text(candidateText(c)) }, onClick = { onLine(c.lapId); open = false }) }
        }
    }
}

private fun candidateText(c: LineCandidate) = "${formatLapTime(c.timeMs)} · ${c.driverName ?: "–"} · ${formatDateTime(c.drivenAtMs)}"

@Composable
private fun LinePreview(line: List<LapSample>, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        if (line.size < 2) {
            Text("keine Linie", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            return@Box
        }
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val projection = MapProjection(line.map { it.x }, line.map { it.z }, size.width, size.height, 0f)
            val path = Path()
            line.forEachIndexed { i, s ->
                val (px, py) = projection.map(s.x, s.z)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, RaceColors.TextPrimary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val (sx, sy) = projection.map(line.first().x, line.first().z)
            drawCircle(GoodGreen, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(sx, sy))
        }
    }
}
