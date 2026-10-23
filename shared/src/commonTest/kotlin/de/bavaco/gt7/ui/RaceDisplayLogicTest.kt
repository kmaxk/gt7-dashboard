package de.bavaco.gt7.ui

import de.bavaco.gt7.session.TireState
import de.bavaco.gt7.session.tireState
import kotlin.test.Test
import kotlin.test.assertEquals

class RaceDisplayLogicTest {
    private fun lit(rpm: Float) = shiftLights(rpm, alertMin = 6000, alertMax = 7000).count { it != LedColor.Off }

    @Test fun shiftLightsFillFromOneBandBelowAlertMinToAlertMax() {
        assertEquals(0, lit(5000f))
        assertEquals(4, lit(6000f))
        assertEquals(7, lit(7000f))
        assertEquals(7, lit(7600f))
    }

    @Test fun shiftLightColoursRunGreenYellowRed() {
        assertEquals(
            listOf(LedColor.Green, LedColor.Green, LedColor.Green, LedColor.Yellow, LedColor.Yellow, LedColor.Red, LedColor.Red),
            shiftLights(7000f, 6000, 7000),
        )
    }

    @Test fun noAlertValuesKeepsLightsOff() {
        assertEquals(List(SHIFT_LEDS) { LedColor.Off }, shiftLights(8000f, 0, 0))
    }

    @Test fun missingAlertMinUsesAFifthOfAlertMax() {
        // band 1400 → strip starts at 4200
        assertEquals(0, shiftLights(4200f, 0, 7000).count { it != LedColor.Off })
        assertEquals(7, shiftLights(7000f, 0, 7000).count { it != LedColor.Off })
    }

    @Test fun tireStates() {
        assertEquals(TireState.Cold, tireState(40f))
        assertEquals(TireState.Ok, tireState(80f))
        assertEquals(TireState.Warm, tireState(100f))
        assertEquals(TireState.Hot, tireState(120f))
    }
}
