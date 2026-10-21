package de.bavaco.gt7.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test fun lapTime() {
        assertEquals("1:42.381", formatLapTime(102381))
        assertEquals("0:59.005", formatLapTime(59005))
        assertEquals("–:––.–––", formatLapTime(null))
        assertEquals("–:––.–––", formatLapTime(0))
        assertEquals("–:––.–––", formatLapTime(-1))
    }

    @Test fun delta() {
        assertEquals("+0.214", formatDelta(214))
        assertEquals("−1.050", formatDelta(-1050))
        assertEquals("+0.000", formatDelta(0))
        assertEquals("–", formatDelta(null))
    }

    @Test fun speed() {
        assertEquals("212", formatSpeed(58.9f, SpeedUnit.KMH))
        assertEquals("132", formatSpeed(58.9f, SpeedUnit.MPH))
    }

    @Test fun decimal() {
        assertEquals("3.5", formatDecimal(3.46f, 1))
        assertEquals("0.07", formatDecimal(0.07f, 2))
        assertEquals("-1.25", formatDecimal(-1.25f, 2))
        assertEquals("12", formatDecimal(11.6f, 0))
        assertEquals("–", formatDecimal(Float.NaN, 1))
        assertEquals("–", formatDecimal(Float.POSITIVE_INFINITY, 2))
        assertEquals("–", formatDecimal(Float.NEGATIVE_INFINITY, 0))
    }
}
