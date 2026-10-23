package de.bavaco.gt7.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import de.bavaco.gt7.session.LiveState
import de.bavaco.gt7.session.Opponent
import de.bavaco.gt7.session.TireState
import de.bavaco.gt7.session.isShiftPoint
import de.bavaco.gt7.session.tireState
import de.bavaco.gt7.storage.CarCatalog
import de.bavaco.gt7.telemetry.TelemetryFrame
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val PanelShape = RoundedCornerShape(4.dp)
private val Dim = RaceColors.Dim
private val LedYellow = RaceColors.LapA
private val LampGreen = RaceColors.Gain.copy(alpha = 0.35f)
private val ColdBlue = RaceColors.LapB
private val BoxCyan = RaceColors.LapB
private val BoxYellow = RaceColors.LapA

/** Race display: shift lights, gear centre stage, tyres left, lap times right; carbon panels like the rest of the app. */
@Composable
fun RaceDisplay(
    state: LiveState,
    unit: SpeedUnit,
    selection: DriverSelection,
    cars: CarCatalog,
    theoreticalBestMs: Int? = null,
    onSelectDriver: (Long) -> Unit = {},
    onSelectOpponent: (Opponent) -> Unit = {},
    onCreateDriver: (String) -> Unit = {},
) {
    val f = state.frame
    val labels = opponentLabels(selection.opponent, selection.active?.name ?: "")
    BoxWithConstraints(Modifier.fillMaxSize().carbon()) {
        val s = dashboardScale(maxWidth.value, maxHeight.value, LocalCompact.current)
        val gap = 8.dp * s
        Column(Modifier.fillMaxSize().padding(12.dp * s), verticalArrangement = Arrangement.spacedBy(gap)) {
            DriverPicker(selection, s, onSelectDriver, onSelectOpponent, onCreateDriver)
            Row(Modifier.fillMaxWidth().height(48.dp * s), horizontalArrangement = Arrangement.spacedBy(gap)) {
                ShiftLeds(f, outsideFirst = true, s, Modifier.weight(0.37f))
                DeltaBox(state.deltaMs, labels.deltaLabel, s, Modifier.weight(0.26f).fillMaxHeight())
                ShiftLeds(f, outsideFirst = false, s, Modifier.weight(0.37f))
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.weight(0.37f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Lamps(f, s, Modifier.fillMaxWidth())
                    Tires(state, f?.let { cars.name(it.carCode) }, s, Modifier.fillMaxWidth().weight(1f))
                }
                CenterStack(state, unit, s, Modifier.weight(0.26f).fillMaxHeight())
                Column(Modifier.weight(0.37f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    SessionPanel(state, s, Modifier.fillMaxWidth())
                    LapTimes(state, labels.bestLabel, bestCellTimeMs(selection.reference, selection.opponent, f?.bestLapMs), theoreticalBestMs, s, Modifier.fillMaxWidth().weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth().height(96.dp * s), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(0.63f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    ValueBox("ÖL °C", f?.oilTempC?.let { formatDecimal(it, 0) }, BoxCyan, s)
                    ValueBox("WASSER °C", f?.waterTempC?.let { formatDecimal(it, 0) }, BoxCyan, s)
                    ValueBox("ÖLDRUCK", f?.oilPressureBar?.let { formatDecimal(it, 1) }, BoxYellow, s)
                    ValueBox("BOOST", f?.takeIf { it.hasTurbo }?.let { formatDecimal(it.boostBar, 2) }, BadRed, s)
                    ValueBox("ZIEL", state.fuelMarginLaps?.let { formatDecimal(it, 1) }, GoodGreen, s)
                }
                FuelPanel(state, s, Modifier.weight(0.37f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ShiftLeds(f: TelemetryFrame?, outsideFirst: Boolean, scale: Float, modifier: Modifier) {
    val leds = if (f == null) List(SHIFT_LEDS) { LedColor.Off } else shiftLights(f.rpm, f.rpmAlertMin, f.rpmAlertMax)
    val limiter = f != null && isShiftPoint(f.rpm, f.rpmAlertMax)
    val blink = blinkAlpha().value
    Row(modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(5.dp * scale)) {
        (if (outsideFirst) leds else leds.reversed()).forEach { led ->
            val color = when (led) {
                LedColor.Off -> Dim
                LedColor.Green -> GoodGreen
                LedColor.Yellow -> LedYellow
                LedColor.Red -> BadRed
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(1.dp * scale)).background(if (limiter) color.copy(alpha = blink) else color))
        }
    }
}

@Composable
private fun DeltaBox(deltaMs: Int?, label: String, scale: Float, modifier: Modifier) {
    val color = if (deltaMs == null) Dim else if (deltaMs < 0) GoodGreen else BadRed
    Box(modifier.clip(RoundedCornerShape(2.dp * scale)).background(color), contentAlignment = Alignment.Center) {
        Text(if (deltaMs == null) label else formatDelta(deltaMs), style = digits(40.sp * scale), color = Color.White, maxLines = 1)
    }
}

/** Filled carbon panel with an [edge] stripe on the left and an optional title at the top left. */
@Composable
private fun Section(
    title: String?, scale: Float, modifier: Modifier, titleSize: Int = 12, edge: Color = RaceColors.Accent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.clip(PanelShape).background(RaceColors.Surface)
            .drawBehind { drawRect(edge, size = Size(3.dp.toPx() * scale, size.height)) }
            .padding(start = 13.dp * scale, top = 10.dp * scale, end = 10.dp * scale, bottom = 10.dp * scale),
        verticalArrangement = Arrangement.spacedBy(4.dp * scale),
    ) {
        if (title != null) SectionTitle(title, scale, titleSize)
        content()
    }
}

@Composable
private fun SectionTitle(title: String, scale: Float, size: Int = 12) {
    Text(
        title, fontSize = size.sp * scale, fontWeight = if (size > 12) FontWeight.Bold else FontWeight.SemiBold,
        letterSpacing = 0.14.em, color = if (size > 12) Color.White else RaceColors.TextMuted, maxLines = 1,
    )
}

@Composable
private fun Lamps(f: TelemetryFrame?, scale: Float, modifier: Modifier) {
    val flags = f?.carFlags
    Section(null, scale, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            Lamp("LICHT", flags?.lights == true, GoodGreen, scale)
            Lamp("FERNL.", flags?.highBeam == true, BoxCyan, scale)
            Lamp("TCS", flags?.tcs == true, WarnAmber, scale)
            Lamp("ASM", flags?.asm == true, WarnAmber, scale)
            Lamp("H-BREMSE", flags?.handBrake == true, BadRed, scale)
        }
    }
}

@Composable
private fun RowScope.Lamp(text: String, on: Boolean, color: Color, scale: Float) {
    Box(
        Modifier.weight(1f).height(38.dp * scale).clip(RoundedCornerShape(2.dp * scale)).background(if (on) LampGreen else RaceColors.SurfaceRaised)
            .border(1.dp * scale, if (on) color else Color.Transparent, RoundedCornerShape(2.dp * scale)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 11.sp * scale, color = if (on) color else Color.Gray, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Tires(state: LiveState, carName: String?, scale: Float, modifier: Modifier) {
    val t = state.frame?.tireTempC
    Section("REIFEN", scale, modifier, titleSize = 15) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    Tire(t?.fl, state.slip.getOrNull(0), true, scale, Modifier.weight(1f))
                    Tire(t?.fr, state.slip.getOrNull(1), false, scale, Modifier.weight(1f))
                }
                Row(Modifier.weight(1f)) {
                    Tire(t?.rl, state.slip.getOrNull(2), true, scale, Modifier.weight(1f))
                    Tire(t?.rr, state.slip.getOrNull(3), false, scale, Modifier.weight(1f))
                }
            }
            if (carName != null) {
                Text(
                    carName, Modifier.align(Alignment.Center).clip(RoundedCornerShape(2.dp * scale)).background(RaceColors.SurfaceRaised).padding(horizontal = 10.dp * scale, vertical = 3.dp * scale),
                    fontSize = 12.sp * scale, color = Color.White, maxLines = 1,
                )
            }
        }
    }
}

/** Temperature, a coloured tyre block and the slip; [left] mirrors the layout for the left-hand wheels. */
@Composable
private fun Tire(tempC: Float?, slip: Float?, left: Boolean, scale: Float, modifier: Modifier) {
    val color = when (tempC?.let { tireState(it) }) {
        null -> Dim
        TireState.Cold -> ColdBlue
        TireState.Ok -> GoodGreen
        TireState.Warm -> WarnAmber
        TireState.Hot -> BadRed
    }
    val slipText = slip?.let { "${formatDecimal(it * 100f, 0)}%" } ?: "–"
    val slipColor = if (isSlipAlertValue(slip)) BadRed else Color.LightGray
    BoxWithConstraints(modifier.fillMaxHeight()) {
        // large where there is room, shrunk to fit a small window: "104°", block and "31%" need about 230 × 96 dp
        val k = scale * minOf(1f, maxWidth / (230.dp * scale), maxHeight / (96.dp * scale))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp * k, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            val temp: @Composable () -> Unit = { Text(tempC?.let { "${it.toInt()}°" } ?: "–", style = digits(64.sp * k), color = Color.White, maxLines = 1) }
            val block: @Composable () -> Unit = { Box(Modifier.width(26.dp * k).height(80.dp * k).clip(RoundedCornerShape(3.dp * k)).background(color)) }
            val slipLabel: @Composable () -> Unit = { Text(slipText, style = digits(24.sp * k), color = slipColor, maxLines = 1) }
            if (left) { temp(); block(); slipLabel() } else { slipLabel(); block(); temp() }
        }
    }
}

private fun isSlipAlertValue(slip: Float?) = de.bavaco.gt7.session.isSlipAlert(slip)

@Composable
private fun CenterStack(state: LiveState, unit: SpeedUnit, scale: Float, modifier: Modifier) {
    val f = state.frame
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp * scale)).background(RaceColors.SurfaceRaised),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val hint = gearHint(f?.gear, f?.suggestedGear ?: 15)
            // the gear fills the panel: the font follows the space left above rpm and hint
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val size = with(LocalDensity.current) { min(maxHeight * 0.95f, maxWidth * 1.6f).toSp() }
                Text(gearText(f?.gear), style = digits(size), color = Color.White, maxLines = 1, softWrap = false)
            }
            Text(f?.rpm?.let { formatDecimal(it, 0) } ?: "–", style = digits(20.sp * scale), color = Color.White)
            Text(hint?.let { "→ $it" } ?: " ", style = digits(18.sp * scale), color = WarnAmber)
        }
        HBar(f?.throttle ?: 0f, GoodGreen, height = 8.dp * scale)
        HBar(f?.brake ?: 0f, BadRed, height = 8.dp * scale)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp * scale)).background(RaceColors.SurfaceRaised)) {
            Box(Modifier.fillMaxWidth().height(41.dp * scale), contentAlignment = Alignment.Center) {
                Text(f?.let { formatSpeed(it.speedMps, unit) } ?: "–", style = digits(32.sp * scale), color = Color.White)
            }
            Box(Modifier.fillMaxWidth().height(3.dp * scale).background(RaceColors.Accent))
        }
        Text(if (unit == SpeedUnit.KMH) "km/h" else "mph", Modifier.fillMaxWidth(), fontSize = 12.sp * scale, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SessionPanel(state: LiveState, scale: Float, modifier: Modifier) {
    val f = state.frame
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    Section(null, scale, modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(formatLapTime(state.lapTimeMs).substringBefore('.'), "RUNDE", scale)
            Stat(f?.let { if (it.totalPositions > 0 && it.racePosition > 0) "${it.racePosition}/${it.totalPositions}" else "–" } ?: "–", "POS", scale)
            Stat(f?.let { lapText(it.currentLap, it.totalLaps) } ?: "–", "LAP", scale)
            Stat("${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}", "UHR", scale)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, scale: Float) {
    Column {
        Text(value, style = digits(26.sp * scale), color = Color.White, maxLines = 1)
        Text(label, fontSize = 11.sp * scale, color = Color.Gray)
    }
}

@Composable
private fun LapTimes(state: LiveState, bestLabel: String, bestMs: Int?, theoreticalMs: Int?, scale: Float, modifier: Modifier) {
    val f = state.frame
    val predicted = state.predictedLapMs
    val d = state.deltaMs
    Section("RUNDENZEITEN", scale, modifier) {
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.End) {
            val estColor = if (d == null) Color.White else if (d < 0) GoodGreen else BadRed
            TimeLine(formatLapTime(predicted ?: state.lapTimeMs), if (predicted != null) "PROGNOSE" else "AKTUELL", estColor, 46.sp * scale, scale)
            TimeLine(formatLapTime(f?.lastLapMs?.takeIf { it > 0 }), "LETZTE", Color.White, 36.sp * scale, scale)
            TimeLine(formatLapTime(bestMs), bestLabel, Color.White, 36.sp * scale, scale)
            TimeLine(formatLapTime(theoreticalMs), "THEORETISCH", RaceColors.Best, 36.sp * scale, scale)
        }
    }
}

@Composable
private fun TimeLine(value: String, label: String, color: Color, size: TextUnit, scale: Float) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, style = digits(size), color = if (value.startsWith("–")) Dim else color, maxLines = 1, softWrap = false)
        Text(label, fontSize = 11.sp * scale, color = Color.Gray)
    }
}

@Composable
private fun RowScope.ValueBox(label: String, value: String?, color: Color, scale: Float) {
    Column(Modifier.weight(1f).fillMaxHeight().clip(PanelShape).background(RaceColors.Surface)) {
        Box(Modifier.fillMaxWidth().height(4.dp * scale).background(color))
        Text(label, Modifier.padding(start = 8.dp * scale, top = 4.dp * scale), fontSize = 15.sp * scale, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(value ?: "– –", style = digits(34.sp * scale), color = if (value == null) color.copy(alpha = 0.5f) else Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun FuelPanel(state: LiveState, scale: Float, modifier: Modifier) {
    val f = state.frame
    val fraction = f?.takeIf { it.fuelCapacity > 0f }?.let { it.fuelLevel / it.fuelCapacity } ?: 0f
    Section(null, scale, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            SectionTitle("TANK", scale)
            FuelBar(fraction, scale, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Stat(state.fuelLapsRemaining?.let { formatDecimal(it, 1) } ?: "–", "RESTRUNDEN", scale)
            Column(horizontalAlignment = Alignment.End) {
                Text(f?.let { fuelText(it.fuelLevel, it.fuelCapacity) } ?: "–", style = digits(26.sp * scale), color = Color.White, maxLines = 1)
                Text("FÜLLSTAND", fontSize = 11.sp * scale, color = Color.Gray)
            }
        }
    }
}

/** Tank level as one bar, with quarter marks. */
@Composable
private fun FuelBar(fraction: Float, scale: Float, modifier: Modifier) {
    Box(modifier.height(8.dp * scale).clip(RoundedCornerShape(1.dp * scale)).background(Dim)) {
        // a broken packet can carry NaN, which a width fraction cannot take
        Box(Modifier.fillMaxWidth(if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f).fillMaxHeight().background(LedYellow))
        Row(Modifier.fillMaxSize()) {
            repeat(4) { i ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (i > 0) Box(Modifier.width(2.dp * scale).fillMaxHeight().background(RaceColors.Surface))
                }
            }
        }
    }
}
