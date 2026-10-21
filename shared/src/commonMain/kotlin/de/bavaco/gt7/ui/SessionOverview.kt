package de.bavaco.gt7.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bavaco.gt7.storage.CarCatalog
import de.bavaco.gt7.storage.SessionSummary

/** Filter and sorting of the session overview; held by App so they survive tab switches. */
class SessionOverviewState {
    var filter by mutableStateOf(SessionFilter())
    var sort by mutableStateOf(SessionSort())
}

private val SEARCH_WIDTH = 280.dp

private val WEIGHTS = mapOf(
    SessionColumn.Track to 2f, SessionColumn.Driver to 1.4f, SessionColumn.Car to 2.4f, SessionColumn.Date to 1.5f,
    SessionColumn.Laps to 0.7f, SessionColumn.Best to 1.1f, SessionColumn.Duration to 0.8f,
)

/**
 * All sessions as a table with track and car filter; a click on a column header sorts, a click on a row opens the session.
 * [restrictTo] narrows the list to the sessions a second lap may come from (same track as lap A, or A's session).
 */
@Composable
fun SessionOverview(
    sessions: List<SessionSummary>, cars: CarCatalog, state: SessionOverviewState, restrictTo: SessionSummary?,
    onClearRestriction: () -> Unit, onOpen: (Long) -> Unit, modifier: Modifier = Modifier,
) {
    val base = when {
        restrictTo == null -> sessions
        restrictTo.trackId != null -> sessions.filter { it.trackId == restrictTo.trackId }
        else -> sessions.filter { it.id == restrictTo.id }
    }
    val rows = sessionRows(base, state.filter, state.sort, cars::name)
    // phones drop the duration
    val columns = if (LocalCompact.current) SessionColumn.entries - SessionColumn.Duration else SessionColumn.entries

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = { state.filter = state.filter.copy(query = it) },
                placeholder = { Text("Suchen…") },
                singleLine = true,
                modifier = Modifier.width(SEARCH_WIDTH),
            )
            TrackFilterMenu(sessions, state)
            CarFilterMenu(sessions, cars, state)
            Text("${rows.size} von ${sessions.size} Sitzungen", style = MaterialTheme.typography.bodySmall)
        }
        if (restrictTo != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nur ${restrictTo.trackName ?: "diese Sitzung"} – für Runde B", style = MaterialTheme.typography.bodySmall)
                TextButton(onClearRestriction) { Text("Filter aufheben") }
            }
        }
        Row {
            columns.forEach { c ->
                val arrow = if (state.sort.column == c) (if (state.sort.descending) " ▼" else " ▲") else ""
                Text(
                    c.title + arrow, Modifier.cell(this, c).clickable { state.sort = state.sort.toggle(c) }.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1,
                )
            }
        }
        HorizontalDivider()
        if (rows.isEmpty()) Text(if (sessions.isEmpty()) "Noch keine Sitzung gefahren." else "Keine Sitzung passt zum Filter.")
        LazyColumn {
            items(rows, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(s.id) }.padding(vertical = 8.dp)) {
                    columns.forEach { c -> Text(cellText(s, c, cars), Modifier.cell(this, c), maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = if (c.mono) FontFamily.Monospace else null) }
                }
            }
        }
    }
}

private val SessionColumn.mono get() = this == SessionColumn.Laps || this == SessionColumn.Best || this == SessionColumn.Duration

private fun Modifier.cell(row: RowScope, column: SessionColumn): Modifier = with(row) { weight(WEIGHTS.getValue(column)).padding(end = 8.dp) }

private fun cellText(s: SessionSummary, column: SessionColumn, cars: CarCatalog): String = when (column) {
    SessionColumn.Track -> listOfNotNull(s.name, s.trackName ?: "Unbekannte Strecke").joinToString(" · ")
    SessionColumn.Driver -> s.driverName ?: "–"
    SessionColumn.Car -> cars.name(s.carCode)
    SessionColumn.Date -> formatDateTime(s.startedAtMs)
    SessionColumn.Laps -> s.lapCount.toString()
    SessionColumn.Best -> formatLapTime(s.bestLapMs)
    SessionColumn.Duration -> formatDuration(s)
}

/** `Quali · Nürburgring GP · Max · 180SX Type X '96 · 09.10.2025 10:53`, the open session above its laps. */
fun sessionLine(s: SessionSummary, cars: CarCatalog): String =
    listOfNotNull(s.name, s.trackName ?: "Unbekannte Strecke", s.driverName, cars.name(s.carCode), formatDateTime(s.startedAtMs)).joinToString(" · ")

@Composable
private fun TrackFilterMenu(sessions: List<SessionSummary>, state: SessionOverviewState) {
    val tracks = sessions.mapNotNull { s -> s.trackId?.let { it to (s.trackName ?: "") } }.distinct().sortedBy { it.second.lowercase() }
    val options = listOf<Pair<String, TrackFilter>>("Alle Strecken" to TrackFilter.All) +
        tracks.map { (id, name) -> name to TrackFilter.Of(id) } +
        (if (sessions.any { it.trackId == null }) listOf("Ohne Strecke" to TrackFilter.None) else emptyList())
    val label = options.firstOrNull { it.second == state.filter.track }?.first ?: "Alle Strecken"
    FilterMenu(label, options.map { (name, f) -> name to { state.filter = state.filter.copy(track = f) } })
}

@Composable
private fun CarFilterMenu(sessions: List<SessionSummary>, cars: CarCatalog, state: SessionOverviewState) {
    val codes = sessions.map { it.carCode }.distinct().sortedBy { cars.name(it).lowercase() }
    val options = listOf<Pair<String, Int?>>("Alle Autos" to null) + codes.map { cars.name(it) to it }
    val label = options.firstOrNull { it.second == state.filter.carCode }?.first ?: "Alle Autos"
    FilterMenu(label, options.map { (name, code) -> name to { state.filter = state.filter.copy(carCode = code) } })
}

@Composable
private fun FilterMenu(label: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(shape = RaceButtonShape, onClick = { open = true }) {
            Text(label, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            RaceIcon(RaceIconKind.Expand, size = 8.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (name, select) -> DropdownMenuItem(text = { Text(name) }, onClick = { select(); open = false }) }
        }
    }
}
