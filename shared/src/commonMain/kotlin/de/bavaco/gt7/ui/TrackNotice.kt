package de.bavaco.gt7.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bavaco.gt7.session.TrackNotice
import kotlinx.coroutines.delay

private const val NOTICE_MS = 10_000L

/** Which track the session got; disappears by itself, nothing has to be confirmed while driving. */
@Composable
fun TrackNoticeBanner(notice: TrackNotice, onReject: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(notice) {
        delay(NOTICE_MS)
        onDismiss()
    }
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Panel).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(trackNoticeText(notice), fontSize = 18.sp, color = RaceColors.TextPrimary)
        if (!notice.isNew) TextButton(onClick = onReject) { Text("Andere Strecke", color = RaceColors.Accent) }
        TextButton(onClick = onDismiss) { Text("✕", color = RaceColors.TextMuted) }
    }
}
