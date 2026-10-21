package de.stiller.gt7.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.stiller.gt7.session.InputShares
import de.stiller.gt7.storage.LapSummary
import kotlin.math.roundToInt

private val LapTimeChartHeight = 120.dp

/** Lap-time chart and lap table of the open session; [stats] fill in Vmax, sectors and the theoretical best. */
@Composable
fun SessionLaps(laps: List<LapSummary>, shares: Map<Long, InputShares>, stats: LapTableStats?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LapTimeChart(laps, Modifier.fillMaxWidth().height(LapTimeChartHeight))
        LapTable(laps, shares, stats)
    }
}

/** Valid lap times of the open session over the lap number; hidden with fewer than two. */
@Composable
private fun LapTimeChart(laps: List<LapSummary>, modifier: Modifier) {
    val valid = laps.filter { it.isValid }
    if (valid.size < 2) return
    val series = remember(laps) { listOf(Series(valid.map { it.number.toFloat() to it.timeMs / 1000f }, LapAColor)) }
    LineChart("Rundenzeiten (gültige Runden, s)", series, modifier = modifier)
}

private val BestSector = RaceColors.Best

/** Laps of the open session: time, gap to its best valid lap, Vmax, sectors (best purple), fuel and pedal shares. */
@Composable
private fun LapTable(laps: List<LapSummary>, shares: Map<Long, InputShares>, stats: LapTableStats?) {
    if (laps.isEmpty()) return
    val best = laps.filter { it.isValid }.minOfOrNull { it.timeMs }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
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
