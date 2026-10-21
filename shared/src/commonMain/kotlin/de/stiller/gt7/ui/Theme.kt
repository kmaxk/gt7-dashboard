package de.stiller.gt7.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val GoodGreen = Color(0xFF4CAF50)
val BadRed = Color(0xFFF44336)
val WarnAmber = Color(0xFFFFC107)
val Panel = Color(0xFF1B1D22)

private val Colors = darkColorScheme(
    background = Color(0xFF0F1115),
    surface = Panel,
    primary = Color(0xFF4FC3F7),
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1),
)

fun digits(size: TextUnit) =
    TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size)

@Composable
fun Gt7Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
