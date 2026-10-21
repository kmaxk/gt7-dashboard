package de.stiller.gt7.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import gt7dashboard.shared.generated.resources.Res
import gt7dashboard.shared.generated.resources.barlow_condensed_bold
import gt7dashboard.shared.generated.resources.barlow_condensed_medium
import gt7dashboard.shared.generated.resources.barlow_condensed_regular
import gt7dashboard.shared.generated.resources.barlow_condensed_semibold
import org.jetbrains.compose.resources.Font

/** The motorsport palette: carbon black, red accent, yellow/cyan for laps A/B, violet for best values. */
object RaceColors {
    val Carbon = Color(0xFF0A0A0A)
    val Surface = Color(0xFF141414)
    val SurfaceRaised = Color(0xFF1F1F1F)
    val Line = Color(0xFF262626)
    val TextPrimary = Color(0xFFF2F2F2)
    val TextMuted = Color(0xFF9A9A9A)
    val Accent = Color(0xFFE10600)
    val LapA = Color(0xFFFFD400)
    val LapB = Color(0xFF00D2FF)
    val Loss = Color(0xFFE10600)
    val Gain = Color(0xFF00E676)
    val Best = Color(0xFFB14CFF)
    val Warn = Color(0xFFFFC107)
    /** Switched-off lamps and LEDs of the race display. */
    val Dim = Color(0xFF3A3A3A)
    /** Faint stripes of the carbon background. */
    val CarbonWeave = Color(0x05FFFFFF)
}

// earlier names, kept for the code that uses them
val GoodGreen = RaceColors.Gain
val BadRed = RaceColors.Loss
val WarnAmber = RaceColors.Warn
val Panel = RaceColors.Surface
val LapAColor = RaceColors.LapA
val LapBColor = RaceColors.LapB

fun raceColorScheme(): ColorScheme = darkColorScheme(
    primary = RaceColors.Accent,
    onPrimary = RaceColors.TextPrimary,
    secondary = RaceColors.LapB,
    background = RaceColors.Carbon,
    onBackground = RaceColors.TextPrimary,
    surface = RaceColors.Surface,
    onSurface = RaceColors.TextPrimary,
    surfaceVariant = RaceColors.SurfaceRaised,
    onSurfaceVariant = RaceColors.TextMuted,
    surfaceContainer = RaceColors.Surface,
    surfaceContainerHigh = RaceColors.SurfaceRaised,
    secondaryContainer = RaceColors.Accent,
    onSecondaryContainer = RaceColors.TextPrimary,
    outline = RaceColors.Line,
    outlineVariant = RaceColors.Line,
    error = RaceColors.Loss,
)

/** Barlow Condensed (SIL Open Font License, see composeResources/font/OFL.txt), loaded once by [Gt7Theme]. */
private val LocalRaceFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** The app's typeface. */
@Composable
fun raceFontFamily(): FontFamily = LocalRaceFont.current

@Composable
private fun loadRaceFont(): FontFamily = FontFamily(
    Font(Res.font.barlow_condensed_regular, FontWeight.Normal),
    Font(Res.font.barlow_condensed_medium, FontWeight.Medium),
    Font(Res.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(Res.font.barlow_condensed_bold, FontWeight.Bold),
)

private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family), displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family), headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family), headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family), titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family), bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family), bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family), labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/** Angular controls: 2 dp corners, 4 dp for large surfaces. */
private val RaceShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** Bold numbers with digits of equal width, so times line up. */
@Composable
fun digits(size: TextUnit, family: FontFamily = raceFontFamily()) = TextStyle(
    fontFamily = family, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size, fontFeatureSettings = "tnum",
)

/** The carbon background: fine diagonal stripes on [RaceColors.Carbon]. */
fun Modifier.carbon(): Modifier = drawBehind {
    drawRect(RaceColors.Carbon)
    val step = 4.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(RaceColors.CarbonWeave, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = step / 2)
        x += step
    }
}

@Composable
fun Gt7Theme(content: @Composable () -> Unit) {
    val font = loadRaceFont()
    CompositionLocalProvider(LocalRaceFont provides font) {
        MaterialTheme(
            colorScheme = raceColorScheme(),
            typography = Typography().withFamily(font),
            shapes = RaceShapes,
            content = content,
        )
    }
}
