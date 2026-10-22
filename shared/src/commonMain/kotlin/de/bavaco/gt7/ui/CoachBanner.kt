package de.bavaco.gt7.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bavaco.gt7.session.CoachTip

const val COACH_SETTING = "liveCoach"

/** Live tip of the race engineer: the corner ahead, its distance and the main cause. */
@Composable
fun CoachBanner(tip: CoachTip, language: TipLanguage, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(6.dp)).background(Panel).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (tip.inM > 0) Text("${tip.inM} m", fontSize = 18.sp, color = WarnAmber)
        Text(EngineerText.liveTip(tip.report, language), fontSize = 30.sp, color = WarnAmber)
    }
}
