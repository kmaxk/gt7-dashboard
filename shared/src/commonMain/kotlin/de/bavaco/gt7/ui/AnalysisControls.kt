package de.bavaco.gt7.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private const val ZOOM_STEP = 0.5f

/** Axis switch and zoom buttons above the charts. */
@Composable
fun ChartToolbar(viewport: AnalysisViewport, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = viewport.mode == AxisMode.DISTANCE, onClick = { viewport.switchMode(AxisMode.DISTANCE) }, label = { Text("Distanz") })
        FilterChip(selected = viewport.mode == AxisMode.TIME, onClick = { viewport.switchMode(AxisMode.TIME) }, label = { Text("Zeit") })
        OutlinedButton(shape = RaceButtonShape, onClick = { viewport.zoom(ZOOM_STEP) }) { Text("+") }
        OutlinedButton(shape = RaceButtonShape, onClick = { viewport.zoom(1f / ZOOM_STEP) }, enabled = viewport.isZoomed) { Text("−") }
        TextButton(onClick = { viewport.resetZoom() }, enabled = viewport.isZoomed) { Text("Gesamt") }
        Text(
            if (viewport.mode == AxisMode.DISTANCE) "x in m" else "x in s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/** Play/pause, a slider over the longer lap and the playback time; runs the clock while playing. */
@Composable
fun PlaybackBar(viewport: AnalysisViewport, modifier: Modifier = Modifier) {
    val end = viewport.endMs
    if (end <= 0) return
    LaunchedEffect(viewport, viewport.playing) {
        if (!viewport.playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (viewport.playing) {
            withFrameMillis { now ->
                viewport.seek(viewport.playMs + (now - last).toInt())
                last = now
            }
            if (viewport.playMs >= end) viewport.playing = false
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { viewport.togglePlay() }) { RaceIcon(if (viewport.playing) RaceIconKind.Pause else RaceIconKind.Play, size = 12.dp) }
        Slider(
            value = viewport.playMs.toFloat(),
            onValueChange = { viewport.playing = false; viewport.seek(it.toInt()) },
            valueRange = 0f..end.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = RaceColors.Accent,
                activeTrackColor = RaceColors.Accent,
                inactiveTrackColor = RaceColors.Line,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            formatLapTime(viewport.playMs.coerceAtLeast(1)).dropLast(2),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(64.dp),
        )
    }
}
