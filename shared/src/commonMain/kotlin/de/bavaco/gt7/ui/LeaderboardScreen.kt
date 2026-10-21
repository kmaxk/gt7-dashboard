package de.bavaco.gt7.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.bavaco.gt7.session.Track
import de.bavaco.gt7.storage.CarCatalog
import de.bavaco.gt7.storage.LeaderboardEntry
import de.bavaco.gt7.storage.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LeaderboardRow(val place: Int, val name: String, val timeMs: Int, val gapMs: Int, val carCode: Int, val drivenAtMs: Long)

/** Rows of a sorted leaderboard: 1-based places (equal times share a place) and gap to the leader. */
fun leaderboardRows(entries: List<LeaderboardEntry>): List<LeaderboardRow> {
    val leader = entries.firstOrNull()?.timeMs ?: return emptyList()
    var place = 0
    return entries.mapIndexed { i, e ->
        if (i == 0 || entries[i - 1].timeMs != e.timeMs) place = i + 1
        LeaderboardRow(place, e.driver.name, e.timeMs, e.timeMs - leader, e.carCode, e.drivenAtMs)
    }
}

@Composable
fun LeaderboardScreen(repo: SessionRepository, cars: CarCatalog) {
    // reload whenever sessions change, e.g. a lap was just driven
    val sessions by remember { repo.sessions() }.collectAsState(emptyList())
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var trackId by remember { mutableStateOf<Long?>(null) }
    var carCode by remember { mutableStateOf<Int?>(null) }
    var carsOnTrack by remember { mutableStateOf<List<Int>>(emptyList()) }
    var rows by remember { mutableStateOf<List<LeaderboardRow>>(emptyList()) }

    LaunchedEffect(sessions) {
        tracks = withContext(Dispatchers.Default) { repo.tracks() }
        if (trackId == null || tracks.none { it.id == trackId }) trackId = tracks.firstOrNull()?.id
    }
    LaunchedEffect(trackId, carCode, sessions) {
        val id = trackId ?: return@LaunchedEffect
        carsOnTrack = withContext(Dispatchers.Default) { repo.carsOnTrack(id) }
        if (carCode != null && carCode !in carsOnTrack) carCode = null
        rows = leaderboardRows(withContext(Dispatchers.Default) { repo.leaderboard(id, carCode) })
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tracks.isEmpty()) {
            Text("Noch keine Strecken gespeichert")
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tracks.forEach { t -> FilterChip(selected = t.id == trackId, onClick = { trackId = t.id; carCode = null }, label = { Text(t.name) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = carCode == null, onClick = { carCode = null }, label = { Text("Alle Autos") })
            carsOnTrack.forEach { c -> FilterChip(selected = carCode == c, onClick = { carCode = c }, label = { Text(cars.name(c)) }) }
        }
        val compact = LocalCompact.current
        val columns = if (compact) COMPACT_COLUMNS else COLUMNS
        Row { columns.forEach { c -> Text(c.title, c.modifier(this), style = MaterialTheme.typography.labelMedium) } }
        HorizontalDivider()
        if (rows.isEmpty()) Text("Noch keine gültige Runde auf dieser Strecke")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows) { r ->
                val cells = listOf(
                    "${r.place}.", r.name, formatLapTime(r.timeMs), if (r.gapMs == 0) "" else formatDelta(r.gapMs),
                    cars.name(r.carCode), formatDateTime(r.drivenAtMs),
                )
                Row { columns.forEachIndexed { i, c -> Cell(cells[i], c.modifier(this), mono = i == 2 || i == 3) } }
            }
        }
    }
}

/** A leaderboard column: a fixed [width] on wide screens, a share of the row ([weight]) on phones. */
private class BoardColumn(val title: String, val width: Dp? = null, val weight: Float? = null) {
    fun modifier(row: RowScope): Modifier = with(row) { width?.let { Modifier.width(it) } ?: Modifier.weight(weight!!) }
}

private val COLUMNS = listOf(
    BoardColumn("Platz", 48.dp), BoardColumn("Fahrer", 180.dp), BoardColumn("Zeit", 120.dp), BoardColumn("Rückstand", 110.dp),
    BoardColumn("Auto", 260.dp), BoardColumn("Datum", 160.dp),
)

// phones drop the date
private val COMPACT_COLUMNS = listOf(
    BoardColumn("Platz", weight = 0.5f), BoardColumn("Fahrer", weight = 1.5f), BoardColumn("Zeit", weight = 1f),
    BoardColumn("Rückstand", weight = 1f), BoardColumn("Auto", weight = 2f),
)

@Composable
private fun Cell(text: String, modifier: Modifier, mono: Boolean = false) {
    Text(text, modifier, fontFamily = if (mono) FontFamily.Monospace else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
