package de.bavaco.gt7.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTest {
    @Test
    fun raceColorSchemeUsesTheRacePalette() {
        val scheme = raceColorScheme()
        assertEquals(RaceColors.Accent, scheme.primary)
        assertEquals(RaceColors.Carbon, scheme.background)
        assertEquals(RaceColors.Surface, scheme.surface)
        assertEquals(RaceColors.TextPrimary, scheme.onSurface)
        assertEquals(RaceColors.Line, scheme.outline)
        assertEquals(Color(0xFFFFD400), RaceColors.LapA)
        assertEquals(Color(0xFF00D2FF), RaceColors.LapB)
        assertEquals(Color(0xFFB14CFF), RaceColors.Best)
        assertEquals(Color(0xFFE10600), RaceColors.Accent)
    }
}
