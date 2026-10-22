package de.bavaco.gt7.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bavaco.gt7.session.LiveLap
import de.bavaco.gt7.session.LiveState

/** Lap times only, in very large type: theoretical best on top, then the delta, then every lap of the session, newest first. */
@Composable
fun TimesDisplay(state: LiveState, theoreticalMs: Int?, laps: List<LiveLap>, modifier: Modifier = Modifier) {
    val bestMs = laps.filter { it.isValid }.minOfOrNull { it.timeMs }
    val delta = state.deltaMs
    BoxWithConstraints(modifier.fillMaxSize().background(RaceColors.Carbon)) {
        val s = dashboardScale(maxWidth.value, maxHeight.value, LocalCompact.current)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp * s, vertical = 16.dp * s), verticalArrangement = Arrangement.spacedBy(8.dp * s)) {
            Column {
                Label("THEORETISCH", s)
                Text(formatLapTime(theoreticalMs), style = digits(150.sp * s), color = if (theoreticalMs == null) RaceColors.Dim else RaceColors.Best, maxLines = 1, softWrap = false)
            }
            Column {
                Label("DELTA", s)
                Text(
                    formatDelta(delta), style = digits(150.sp * s), maxLines = 1, softWrap = false,
                    color = if (delta == null) RaceColors.Dim else if (delta < 0) GoodGreen else BadRed,
                )
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(laps.asReversed(), key = { it.number }) { lap ->
                    val color = when {
                        !lap.isValid -> RaceColors.Dim
                        lap.timeMs == bestMs -> GoodGreen
                        else -> Color.White
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp * s), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp * s)) {
                        Text("${lap.number}", style = digits(70.sp * s), color = RaceColors.TextMuted, maxLines = 1)
                        Text(formatLapTime(lap.timeMs), style = digits(90.sp * s), color = color, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
