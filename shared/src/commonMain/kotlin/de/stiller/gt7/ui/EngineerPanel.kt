package de.stiller.gt7.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.stiller.gt7.session.CornerReport
import de.stiller.gt7.session.Debrief
import de.stiller.gt7.storage.ComparisonLap
import kotlin.math.roundToInt

private val Selected = RaceColors.SurfaceRaised

/** Tips and corner table; a click on either selects the corner in the [viewport]. */
@Composable
fun EngineerPanel(state: EngineerState, language: TipLanguage, viewport: AnalysisViewport?, modifier: Modifier = Modifier) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(EngineerText.title(language), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (state is EngineerState.Ready && state.debrief.reports.isNotEmpty()) {
                val total = state.debrief.totalLossMs
                Text(EngineerText.total(total, language), style = MaterialTheme.typography.labelMedium, color = lossColor(total))
            }
        }
        when (state) {
            EngineerState.Computing -> Text(EngineerText.computing(language), color = dim)
            EngineerState.NoTrack -> Text(EngineerText.noTrack(language), color = dim)
            EngineerState.NoComparison -> Text(EngineerText.noComparison(language), color = dim)
            is EngineerState.Ready -> {
                val basis = state.comparison?.let { EngineerText.basis(it, language) }
                    ?: state.pickedTimeMs?.let { EngineerText.pickedBasis(it, language) }
                basis?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = dim) }
                val d = state.debrief
                when {
                    d.reports.isEmpty() -> Text(EngineerText.noCorners(language), color = dim)
                    d.tips.isEmpty() -> Text(EngineerText.allGood(language), color = GoodGreen)
                    else -> d.tips.forEach { tip ->
                        Text(
                            EngineerText.tip(tip, language),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                                .background(if (viewport?.selectedCorner == tip.corner) Selected else Color.Transparent)
                                .clickable(enabled = viewport != null) { viewport?.toggleCorner(tip.corner) }
                                .padding(vertical = 3.dp, horizontal = 4.dp),
                        )
                    }
                }
                if (d.reports.isNotEmpty()) CornerTable(d.reports, language, viewport, Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun lossColor(ms: Int) = when {
    ms > 0 -> BadRed
    ms < 0 -> GoodGreen
    else -> Color.Unspecified
}

/** One row per corner; on a phone three columns, a tap shows the rest of the row. */
@Composable
private fun CornerTable(reports: List<CornerReport>, language: TipLanguage, viewport: AnalysisViewport?, modifier: Modifier) {
    val compact = LocalCompact.current
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val head = MaterialTheme.typography.labelSmall
    var expanded by remember { mutableStateOf<Int?>(null) }
    Column(modifier) {
        Text("Kurven (Runde / Vergleich)", style = MaterialTheme.typography.labelMedium)
        if (compact) {
            val widths = listOf(44.dp, 64.dp)
            CellRow(listOf("Kurve", "Verlust", "Hauptursache"), widths, head, listOf())
            reports.forEach { r ->
                val number = r.corner.number
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (viewport?.selectedCorner == r.corner) Selected else Color.Transparent)
                        .clickable {
                            expanded = if (expanded == number) null else number
                            viewport?.toggleCorner(r.corner)
                        },
                ) {
                    val cause = r.causes.firstOrNull()?.let { EngineerText.cause(it, language) } ?: "–"
                    CellRow(listOf("$number", EngineerText.seconds(r.lossMs, language, signed = true), cause), widths, mono, listOf(Color.Unspecified, lossColor(r.lossMs)))
                    if (expanded == number) {
                        Text(
                            "Bremse ${meterDiff(r.brakeM, r.refBrakeM)} · Scheitel ${kmh(r.apexKmh)} / ${kmh(r.refApexKmh)} km/h · " +
                                "Gas ${meterDiff(r.throttleM, r.refThrottleM)} · Gang ${r.gear} / ${r.refGear}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 44.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        } else {
            val widths = listOf(44.dp, 64.dp, 68.dp, 76.dp, 60.dp, 44.dp)
            CellRow(listOf("Kurve", "Verlust", "Bremse", "Scheitel", "Gas", "Gang"), widths, head, listOf())
            reports.forEach { r ->
                CellRow(
                    listOf(
                        "${r.corner.number}",
                        EngineerText.seconds(r.lossMs, language, signed = true),
                        meterDiff(r.brakeM, r.refBrakeM),
                        "${kmh(r.apexKmh)} / ${kmh(r.refApexKmh)}",
                        meterDiff(r.throttleM, r.refThrottleM),
                        "${r.gear} / ${r.refGear}",
                    ),
                    widths, mono, listOf(Color.Unspecified, lossColor(r.lossMs)),
                    Modifier.fillMaxWidth()
                        .background(if (viewport?.selectedCorner == r.corner) Selected else Color.Transparent)
                        .clickable(enabled = viewport != null) { viewport?.toggleCorner(r.corner) },
                )
            }
            Text(
                "Bremse/Gas: + = später als der Vergleich; Scheitel in km/h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

private fun kmh(v: Float) = v.roundToInt()

/** Cells of fixed [widths]; the last cell without a width takes the rest. */
@Composable
private fun CellRow(cells: List<String>, widths: List<Dp>, style: TextStyle, colors: List<Color>, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 2.dp)) {
        cells.forEachIndexed { i, c ->
            val m = widths.getOrNull(i)?.let { Modifier.width(it) } ?: Modifier.weight(1f)
            Text(c, style = style, color = colors.getOrNull(i) ?: Color.Unspecified, softWrap = false, maxLines = 1, modifier = m)
        }
    }
}
