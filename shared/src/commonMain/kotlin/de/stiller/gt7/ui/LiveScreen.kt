package de.stiller.gt7.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.stiller.gt7.session.ConnectionStatus
import de.stiller.gt7.session.LapSample
import de.stiller.gt7.session.LiveState
import de.stiller.gt7.session.Opponent
import de.stiller.gt7.session.SessionController
import de.stiller.gt7.session.gapTrend
import de.stiller.gt7.session.isShiftPoint
import de.stiller.gt7.session.positionAtTime
import de.stiller.gt7.storage.CarCatalog

enum class LiveLayout { Standard, Race }

/** Live tab: collects the controller's state and shows [LiveDashboard] or [RaceDisplay], switchable top right. */
@Composable
fun LiveScreen(controller: SessionController, unit: SpeedUnit, cars: CarCatalog, layout: LiveLayout, onLayoutChange: (LiveLayout) -> Unit) {
    val state by controller.live.collectAsState()
    val connection by controller.connection.collectAsState()
    val host by controller.ps5Host.collectAsState()
    val drivers by controller.drivers.collectAsState()
    val active by controller.activeDriver.collectAsState()
    val opponent by controller.opponent.collectAsState()
    val reference by controller.reference.collectAsState()
    val trackLine by controller.trackLine.collectAsState()
    val selection = DriverSelection(drivers, active, opponent, reference)
    Box(Modifier.fillMaxSize()) {
        when (layout) {
            LiveLayout.Standard -> LiveDashboard(
                state, connection, host, unit, selection, cars, trackLine,
                onSelectDriver = controller::selectDriver,
                onSelectOpponent = controller::selectOpponent,
                onCreateDriver = controller::createDriver,
            )
            LiveLayout.Race -> Box(Modifier.fillMaxSize()) {
                RaceDisplay(
                    state, unit, selection, cars,
                    onSelectDriver = controller::selectDriver,
                    onSelectOpponent = controller::selectOpponent,
                    onCreateDriver = controller::createDriver,
                )
                ConnectionOverlay(connection, host, 1f, Modifier.matchParentSize())
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = layout == LiveLayout.Standard, onClick = { onLayoutChange(LiveLayout.Standard) }, label = { Text("Standard") })
            FilterChip(selected = layout == LiveLayout.Race, onClick = { onLayoutChange(LiveLayout.Race) }, label = { Text("Renn-Display") })
        }
    }
}

@Composable
private fun ConnectionOverlay(connection: ConnectionStatus, host: String?, scale: Float, modifier: Modifier) {
    if (connection == ConnectionStatus.Receiving) return
    Box(modifier.background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Text(
            if (connection == ConnectionStatus.Searching || host == null) "Suche PS5…" else "Warte auf PS5 ($host)…",
            fontSize = 32.sp * scale,
        )
    }
}

/** Stateless live dashboard (variant A): main column with the instruments, side panel with track map and gap. */
@Composable
fun LiveDashboard(
    state: LiveState,
    connection: ConnectionStatus,
    host: String?,
    unit: SpeedUnit,
    selection: DriverSelection,
    cars: CarCatalog,
    trackLine: List<LapSample>? = null,
    onSelectDriver: (Long) -> Unit = {},
    onSelectOpponent: (Opponent) -> Unit = {},
    onCreateDriver: (String) -> Unit = {},
) {
    val f = state.frame
    // adding is idempotent per packet, so recomposing with the same frame is harmless
    val trace = remember { PedalTrace() }
    val lapPath = remember { LapPath() }
    val deltas = remember { DeltaHistory() }
    if (f != null) {
        trace.add(f.packetId, f.throttle, f.brake)
        lapPath.add(state.lapTimeMs, f.x, f.z)
        state.deltaMs?.let { deltas.add(f.packetId, state.lapTimeMs, it) }
    }
    val activeName = selection.active?.name ?: ""
    val labels = opponentLabels(selection.opponent, activeName)
    val reference = selection.reference
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val s = dashboardScale(maxWidth.value, maxHeight.value, LocalCompact.current)
        // the main column shares the width with the side panel, so its instruments shrink a little
        val m = s * MAIN_COLUMN_SCALE
        Column(Modifier.fillMaxSize().padding(16.dp * s), verticalArrangement = Arrangement.spacedBy(12.dp * s)) {
            DriverPicker(selection, s, onSelectDriver, onSelectOpponent, onCreateDriver)
            RpmBar(f?.rpm ?: 0f, f?.rpmAlertMin ?: 0, f?.rpmAlertMax ?: 0, 30.dp * s)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp * s)) {
                Column(Modifier.weight(0.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp * s)) {
                    Row(Modifier.fillMaxWidth().weight(3f), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label(if (unit == SpeedUnit.KMH) "KM/H" else "MPH", m)
                            Text(f?.let { formatSpeed(it.speedMps, unit) } ?: "–", style = digits(130.sp * m))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Label("GANG", m)
                            Row(verticalAlignment = Alignment.Bottom) {
                                // equal-width slots left and right keep the gear centred whether or not a hint shows
                                val hint = gearHint(f?.gear, f?.suggestedGear ?: NO_SUGGESTION)
                                Text("  ", style = digits(56.sp * m))
                                Text(gearText(f?.gear), style = digits(170.sp * m))
                                Text(hint?.let { "→$it" } ?: "  ", style = digits(56.sp * m), color = WarnAmber)
                            }
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp * m)) {
                            Label("RUNDE", m)
                            Text(f?.let { lapText(it.currentLap, it.totalLaps) } ?: "–", style = digits(44.sp * m))
                            Label("POSITION", m)
                            Text(f?.let { if (it.totalPositions > 0 && it.racePosition > 0) "${it.racePosition}/${it.totalPositions}" else "–" } ?: "–", style = digits(44.sp * m))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().weight(1.2f).clip(RoundedCornerShape(6.dp)).background(Panel).padding(horizontal = 16.dp * m),
                        horizontalArrangement = Arrangement.spacedBy(12.dp * m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val predicted = state.predictedLapMs
                        if (predicted != null) TimeCell("PROGNOSE", formatLapTime(predicted), m)
                        else TimeCell("AKTUELL", formatLapTime(state.lapTimeMs), m)
                        TimeCell("LETZTE", formatLapTime(f?.lastLapMs?.takeIf { it > 0 }), m)
                        TimeCell(labels.bestLabel, formatLapTime(bestCellTimeMs(reference, selection.opponent, f?.bestLapMs)), m)
                        val d = state.deltaMs
                        TimeCell(labels.deltaLabel, formatDelta(d), m, if (d == null) Color.Gray else if (d < 0) GoodGreen else BadRed)
                    }
                    Row(Modifier.fillMaxWidth().weight(2.6f), horizontalArrangement = Arrangement.spacedBy(24.dp * m)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp * m)) {
                            PedalTraceChart(trace.points(), trace.windowPackets, m, Modifier.fillMaxWidth().weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp * m)) {
                                Label("GAS", m, GoodGreen)
                                Label("BREMSE", m, BadRed)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp * m)) {
                            Label("REIFEN °C", m)
                            val t = f?.tireTempC
                            val slip = state.slip
                            Row(horizontalArrangement = Arrangement.spacedBy(36.dp * m)) {
                                TireCell(t?.fl ?: 0f, slip.getOrNull(0), m); TireCell(t?.fr ?: 0f, slip.getOrNull(1), m)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(36.dp * m)) {
                                TireCell(t?.rl ?: 0f, slip.getOrNull(2), m); TireCell(t?.rr ?: 0f, slip.getOrNull(3), m)
                            }
                            // boost sits under the tyres so the pedal trace keeps its width next to the side panel
                            if (f?.hasTurbo == true) {
                                Label("LADEDRUCK", m)
                                Text("${formatDecimal(f.boostBar, 2)} bar", style = digits(30.sp * m))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp * m)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(36.dp * m)) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp * m)) {
                                    Label("BENZIN", m)
                                    Text(f?.let { fuelText(it.fuelLevel, it.fuelCapacity) } ?: "–", style = digits(34.sp * m))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp * m)) {
                                    Label("RESTRUNDEN", m)
                                    Text(state.fuelLapsRemaining?.let { formatDecimal(it, 1) } ?: "–", style = digits(34.sp * m))
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp * m)) {
                                Label("ZIEL", m)
                                val margin = state.fuelMarginLaps
                                Text(fuelMarginText(margin), style = digits(34.sp * m), color = if (margin == null) Color.Gray else if (margin >= 0f) GoodGreen else BadRed)
                            }
                        }
                    }
        
                }
                Column(Modifier.weight(0.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp * s)) {
                    val line = (reference?.samples ?: trackLine)?.map { it.x to it.z } ?: lapPath.points()
                    val ghost = reference?.let { positionAtTime(it.samples, state.lapTimeMs) }
                    LiveMap(line, f?.let { it.x to it.z }, ghost, activeName, labels.opponentName, s, Modifier.fillMaxWidth().weight(1f))
                    val trend = gapTrend(state.deltaMs, f?.let { deltas.before(it.packetId) })
                    OpponentBox(labels, reference, state.deltaMs, trend, selection.opponent == Opponent.Self, cars, s, Modifier.fillMaxWidth())
                }
            }
        }
        ShiftFrame(f != null && isShiftPoint(f.rpm, f.rpmAlertMax), 10.dp * s, Modifier.matchParentSize())
        ConnectionOverlay(connection, host, s, Modifier.matchParentSize())
    }
}

private const val MAIN_COLUMN_SCALE = 0.88f

@Composable
private fun RowScope.TimeCell(label: String, value: String, scale: Float, color: Color = Color.Unspecified) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp * scale)) {
        Label(label, scale)
        // the band shares the width with the side panel: no wrapping, slightly smaller digits
        Text(value, style = digits(34.sp * scale), color = color, softWrap = false, maxLines = 1)
    }
}

private const val NO_SUGGESTION = 15

/** Gear GT7 suggests when it differs from the current one; null without a suggestion (15). */
fun gearHint(gear: Int?, suggested: Int): Int? =
    if (gear == null || suggested !in 1..8 || suggested == gear) null else suggested

internal fun gearText(gear: Int?): String = when {
    gear == null -> "–"
    gear == 0 -> "R"
    gear == 15 -> "N" // GT7 convention, to be verified on hardware
    else -> gear.toString()
}

internal fun lapText(current: Int, total: Int) =
    if (current <= 0) "–" else if (total > 0) "$current/$total" else "$current"

internal fun fuelText(level: Float, capacity: Float) =
    if (capacity > 0f) "${(level / capacity * 100).toInt()} %" else "${level.toInt()} l"

internal fun fuelMarginText(margin: Float?) = when {
    margin == null -> "–"
    margin >= 0f -> "reicht +${formatDecimal(margin, 1)}"
    else -> "fehlt ${formatDecimal(-margin, 1)}"
}
