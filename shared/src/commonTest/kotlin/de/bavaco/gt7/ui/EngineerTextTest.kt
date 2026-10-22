package de.bavaco.gt7.ui

import de.bavaco.gt7.session.Cause
import de.bavaco.gt7.session.Corner
import de.bavaco.gt7.session.CornerReport
import de.bavaco.gt7.storage.ComparisonBasis
import de.bavaco.gt7.storage.ComparisonLap
import de.bavaco.gt7.ui.TipLanguage.DE
import de.bavaco.gt7.ui.TipLanguage.EN
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineerTextTest {
    private fun report(number: Int, lossMs: Int, vararg causes: Cause) =
        CornerReport(Corner(number, 0f, 50f, 100f), lossMs, null, null, 80f, 80f, null, null, 3, 3, causes.toList())

    @Test
    fun everyCauseInGermanAndEnglish() {
        val cases = listOf(
            Cause.BrakeEarly(14) to ("Kurve 4: 14 m später bremsen – kostet 0,21 s" to "Turn 4: brake 14 m later – costs 0.21 s"),
            Cause.BrakeLate(8) to ("Kurve 4: 8 m früher bremsen – kostet 0,21 s" to "Turn 4: brake 8 m earlier – costs 0.21 s"),
            Cause.SlowApex(6) to ("Kurve 4: 6 km/h mehr Scheitelgeschwindigkeit – kostet 0,21 s" to "Turn 4: carry 6 km/h more through the apex – costs 0.21 s"),
            Cause.OverspeedApex(5) to ("Kurve 4: 5 km/h zu schnell am Scheitel – langsamer rein, früher raus – kostet 0,21 s" to
                "Turn 4: 5 km/h too fast at the apex – slow in, fast out – costs 0.21 s"),
            Cause.LateThrottle(18) to ("Kurve 4: früher ans Gas, 18 m später als die Vergleichsrunde – kostet 0,21 s" to
                "Turn 4: get on the throttle earlier, 18 m later than the reference – costs 0.21 s"),
            Cause.GearDiffers(2, 3) to ("Kurve 4: Gang 2 statt 3 – kostet 0,21 s" to "Turn 4: gear 2 instead of 3 – costs 0.21 s"),
        )
        for ((cause, texts) in cases) {
            assertEquals(texts.first, EngineerText.tip(report(4, 210, cause), DE))
            assertEquals(texts.second, EngineerText.tip(report(4, 210, cause), EN))
        }
    }

    @Test
    fun furtherCausesAreListedAfterTheMainOne() {
        val r = report(2, 1234, Cause.BrakeEarly(15), Cause.GearDiffers(2, 3))
        assertEquals("Kurve 2: 15 m später bremsen – kostet 1,23 s (auch: Gang 2 statt 3)", EngineerText.tip(r, DE))
        assertEquals("Turn 2: brake 15 m later – costs 1.23 s (also: gear 2 instead of 3)", EngineerText.tip(r, EN))
    }

    @Test
    fun lossWithoutCause() {
        assertEquals("Kurve 1: Zeitverlust ohne klare Ursache, Linie prüfen – kostet 0,06 s", EngineerText.tip(report(1, 60), DE))
        assertEquals("Turn 1: time lost without a clear cause, check your line – costs 0.06 s", EngineerText.tip(report(1, 60), EN))
    }

    @Test
    fun comparisonBasis() {
        val lap = ComparisonLap(1, 102_318, "Max", ComparisonBasis.SAME_CAR)
        assertEquals("verglichen mit: Max · 1:42.318 · gleiches Auto", EngineerText.basis(lap, DE))
        assertEquals("compared with: Max · 1:42.318 · same car", EngineerText.basis(lap, EN))
        assertEquals("verglichen mit: Max · 1:42.318 · anderes Auto", EngineerText.basis(lap.copy(basis = ComparisonBasis.SAME_DRIVER), DE))
        assertEquals("verglichen mit: Max · 1:42.318 · Referenzrunde", EngineerText.basis(lap.copy(basis = ComparisonBasis.REFERENCE), DE))
        assertEquals("compared with: ? · 1:42.318 · fastest on the track", EngineerText.basis(lap.copy(driverName = null, basis = ComparisonBasis.ANY), EN))
    }

    @Test
    fun secondsFollowTheLanguage() {
        assertEquals("+0,42 s", EngineerText.seconds(420, DE, signed = true))
        assertEquals("−1.05 s", EngineerText.seconds(-1050, EN, signed = true))
        assertEquals("0,05 s", EngineerText.seconds(50, DE))
    }

    @Test
    fun pickedLapBAsBasis() {
        assertEquals("verglichen mit: Runde B · 1:42.318", EngineerText.pickedBasis(102_318, DE))
        assertEquals("compared with: lap B · 1:42.318", EngineerText.pickedBasis(102_318, EN))
    }

    @Test
    fun meterDifferenceIsSigned() {
        assertEquals("+14 m", meterDiff(714f, 700f))
        assertEquals("−15 m", meterDiff(685f, 700f))
        assertEquals("±0 m", meterDiff(700.2f, 700f))
        assertEquals("–", meterDiff(null, 700f))
    }

    @Test
    fun spokenTipWritesUnitsOut() {
        assertEquals("Kurve 4: 14 Meter später bremsen", EngineerText.spoken(report(4, 200, Cause.BrakeEarly(14)), DE))
        assertEquals("Turn 4: carry 5 kilometers per hour more through the apex", EngineerText.spoken(report(4, 200, Cause.SlowApex(5)), EN))
    }
}
