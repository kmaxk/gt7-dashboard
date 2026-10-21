package de.stiller.gt7.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.stiller.gt7.session.LiveState
import de.stiller.gt7.session.Opponent
import de.stiller.gt7.session.isShiftPoint
import de.stiller.gt7.storage.CarCatalog
import de.stiller.gt7.telemetry.TelemetryFrame
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val Black = Color(0xFF000000)
private val Frame = Color(0xFFDDDDDD)
private val Dim = Color(0xFF3A3A3A)
private val LedYellow = Color(0xFFFFD600)
private val LampGreen = Color(0xFF1B5E20)
private val ColdBlue = Color(0xFF00BCD4)
private val BoxCyan = Color(0xFF29B6F6)
private val BoxYellow = Color(0xFFFFEB3B)

/** Race display after the "Lovely Dashboard": shift lights, gear centre stage, tyres left, lap times right. */
@Composable
fun RaceDisplay(
    state: LiveState,
    unit: SpeedUnit,
    selection: DriverSelection,
    cars: CarCatalog,
    onSelectDriver: (Long) -> Unit = {},
    onSelectOpponent: (Opponent) -> Unit = {},
    onCreateDriver: (String) -> Unit = {},
) {
    val f = state.frame
    val labels = opponentLabels(selection.opponent, selection.active?.name ?: "")
    BoxWithConstraints(Modifier.fillMaxSize().background(Black)) {
        val s = dashboardScale(maxWidth.value, maxHeight.value, LocalCompact.current)
        val gap = 8.dp * s
        Column(Modifier.fillMaxSize().padding(12.dp * s), verticalArrangement = Arrangement.spacedBy(gap)) {
            DriverPicker(selection, s, onSelectDriver, onSelectOpponent, onCreateDriver)
            Row(Modifier.fillMaxWidth().height(26.dp * s), horizontalArrangement = Arrangement.spacedBy(gap)) {
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
                    LapTimes(state, labels.bestLabel, bestCellTimeMs(selection.reference, selection.opponent, f?.bestLapMs), s, Modifier.fillMaxWidth().weight(1f))
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
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(if (limiter) color.copy(alpha = blink) else color))
        }
    }
}

@Composable
private fun DeltaBox(deltaMs: Int?, label: String, scale: Float, modifier: Modifier) {
    val color = if (deltaMs == null) Dim else if (deltaMs < 0) GoodGreen else BadRed
    Box(modifier.clip(RoundedCornerShape(topStart = 10.dp * scale, topEnd = 10.dp * scale)).background(color), contentAlignment = Alignment.Center) {
        Text(if (deltaMs == null) label else formatDelta(deltaMs), style = digits(20.sp * scale), color = Color.White, maxLines = 1)
    }
}

/** Rounded, light-framed panel with an optional title set into the top edge. */
@Composable
private fun Section(title: String?, scale: Float, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp * scale)).border(1.5.dp * scale, Frame, RoundedCornerShape(14.dp * scale)).padding(10.dp * scale),
        verticalArrangement = Arrangement.spacedBy(4.dp * scale),
    ) {
        if (title != null) Text(title, Modifier.fillMaxWidth(), fontSize = 12.sp * scale, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
        content()
    }
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
        Modifier.weight(1f).height(38.dp * scale).clip(RoundedCornerShape(6.dp * scale)).background(if (on) LampGreen else Panel)
            .border(1.dp * scale, if (on) color else Color.Transparent, RoundedCornerShape(6.dp * scale)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 11.sp * scale, color = if (on) color else Color.Gray, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Tires(state: LiveState, carName: String?, scale: Float, modifier: Modifier) {
    val t = state.frame?.tireTempC
    Section("REIFEN", scale, modifier) {
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
                    carName, Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Dim).padding(horizontal = 10.dp * scale, vertical = 3.dp * scale),
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
    val slipColor = if (isSlipAlertValue(slip)) BadRed else Color.Gray
    Row(modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(8.dp * scale, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        val temp: @Composable () -> Unit = { Text(tempC?.let { "${it.toInt()}°" } ?: "–", style = digits(22.sp * scale), color = Color.White) }
        val block: @Composable () -> Unit = { Box(Modifier.width(18.dp * scale).height(46.dp * scale).clip(RoundedCornerShape(3.dp * scale)).background(color)) }
        val slipLabel: @Composable () -> Unit = { Text(slipText, fontSize = 12.sp * scale, color = slipColor) }
        if (left) { temp(); block(); slipLabel() } else { slipLabel(); block(); temp() }
    }
}

private fun isSlipAlertValue(slip: Float?) = de.stiller.gt7.session.isSlipAlert(slip)

@Composable
private fun CenterStack(state: LiveState, unit: SpeedUnit, scale: Float, modifier: Modifier) {
    val f = state.frame
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp * scale)).background(Color(0xFF2B2B2B)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val hint = gearHint(f?.gear, f?.suggestedGear ?: 15)
            Text(gearText(f?.gear), style = digits(150.sp * scale), color = Color.White)
            Text(f?.rpm?.let { formatDecimal(it, 0) } ?: "–", style = digits(20.sp * scale), color = Color.White)
            Text(hint?.let { "→ $it" } ?: " ", style = digits(18.sp * scale), color = WarnAmber)
        }
        HBar(f?.throttle ?: 0f, GoodGreen, height = 8.dp * scale)
        HBar(f?.brake ?: 0f, BadRed, height = 8.dp * scale)
        Box(Modifier.fillMaxWidth().height(44.dp * scale).clip(RoundedCornerShape(4.dp * scale)).background(Color.White), contentAlignment = Alignment.Center) {
            Text(f?.let { formatSpeed(it.speedMps, unit) } ?: "–", style = digits(30.sp * scale), color = Black)
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
private fun LapTimes(state: LiveState, bestLabel: String, bestMs: Int?, scale: Float, modifier: Modifier) {
    val f = state.frame
    val predicted = state.predictedLapMs
    val d = state.deltaMs
    Section("RUNDENZEITEN", scale, modifier) {
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.End) {
            val estColor = if (d == null) Color.White else if (d < 0) GoodGreen else BadRed
            TimeLine(formatLapTime(predicted ?: state.lapTimeMs), if (predicted != null) "PROGNOSE" else "AKTUELL", estColor, 46.sp * scale, scale)
            TimeLine(formatLapTime(f?.lastLapMs?.takeIf { it > 0 }), "LETZTE", Color.White, 36.sp * scale, scale)
            TimeLine(formatLapTime(bestMs), bestLabel, Color.White, 36.sp * scale, scale)
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
    Column(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(bottomStart = 16.dp * scale, bottomEnd = 16.dp * scale, topStart = 4.dp * scale, topEnd = 4.dp * scale))
            .border(1.5.dp * scale, color, RoundedCornerShape(bottomStart = 16.dp * scale, bottomEnd = 16.dp * scale, topStart = 4.dp * scale, topEnd = 4.dp * scale))
            .padding(6.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 11.sp * scale, color = color, maxLines = 1)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(value ?: "– –", style = digits(24.sp * scale), color = if (value == null) color.copy(alpha = 0.5f) else Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun FuelPanel(state: LiveState, scale: Float, modifier: Modifier) {
    val f = state.frame
    val fraction = f?.takeIf { it.fuelCapacity > 0f }?.let { it.fuelLevel / it.fuelCapacity } ?: 0f
    Section("TANK", scale, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text("E", fontSize = 10.sp * scale, color = Color.Gray)
            FuelScale(fraction, scale, Modifier.weight(1f))
            Text("F", fontSize = 10.sp * scale, color = Color.Gray)
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

/** Segmented E..F bar like a fuel gauge. */
@Composable
private fun FuelScale(fraction: Float, scale: Float, modifier: Modifier, segments: Int = 10, height: Dp = 8.dp) {
    Row(modifier.height(height * scale), horizontalArrangement = Arrangement.spacedBy(2.dp * scale)) {
        repeat(segments) { i ->
            val on = fraction * segments > i
            Box(Modifier.weight(1f).fillMaxHeight().background(if (on) LedYellow else Dim))
        }
    }
}
