package de.stiller.gt7.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val CardShape = RoundedCornerShape(2.dp)

/** Material buttons are fully rounded by default; the race look keeps them angular. */
val RaceButtonShape = RoundedCornerShape(2.dp)

/** A surface with a 3 dp edge on the left in [accent]; without accent a plain surface. */
@Composable
fun RaceCard(modifier: Modifier = Modifier, accent: Color? = RaceColors.Accent, content: @Composable ColumnScope.() -> Unit) {
    Row(modifier.height(IntrinsicSize.Min).clip(CardShape).background(RaceColors.Surface)) {
        if (accent != null) Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), content = content)
    }
}

/** Small spaced capitals, e.g. RUNDE A. */
@Composable
fun RaceLabel(text: String, modifier: Modifier = Modifier, color: Color = RaceColors.TextMuted) {
    Text(
        text.uppercase(),
        modifier,
        style = TextStyle(fontFamily = raceFontFamily(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em),
        color = color,
        maxLines = 1,
    )
}

/** A large time or number with digits of equal width. */
@Composable
fun RaceNumber(text: String, color: Color = RaceColors.TextPrimary, size: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    Text(text, modifier, style = digits(size, raceFontFamily()), color = color, maxLines = 1)
}

enum class RaceIconKind { Previous, Next, Play, Pause, Expand, Collapse }

/**
 * Small symbols drawn instead of font glyphs: Barlow Condensed has no ◀ ▶ ▾, and the platform's fallback turns them into
 * colour emoji.
 */
@Composable
fun RaceIcon(kind: RaceIconKind, modifier: Modifier = Modifier, color: Color = LocalContentColor.current, size: Dp = 10.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        fun triangle(a: Offset, b: Offset, c: Offset) = drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)
        when (kind) {
            RaceIconKind.Previous -> triangle(Offset(w, 0f), Offset(w, h), Offset(0f, h / 2))
            RaceIconKind.Next, RaceIconKind.Play -> triangle(Offset(0f, 0f), Offset(0f, h), Offset(w, h / 2))
            RaceIconKind.Expand -> triangle(Offset(0f, h * 0.2f), Offset(w, h * 0.2f), Offset(w / 2, h * 0.85f))
            RaceIconKind.Collapse -> triangle(Offset(w * 0.2f, 0f), Offset(w * 0.2f, h), Offset(w * 0.85f, h / 2))
            RaceIconKind.Pause -> {
                drawRect(color, Offset(w * 0.1f, 0f), Size(w * 0.3f, h))
                drawRect(color, Offset(w * 0.6f, 0f), Size(w * 0.3f, h))
            }
        }
    }
}
