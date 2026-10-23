package de.bavaco.gt7.ui

import de.bavaco.gt7.session.Axle
import de.bavaco.gt7.session.Cause
import de.bavaco.gt7.session.Corner
import de.bavaco.gt7.session.CornerReport
import de.bavaco.gt7.session.RaceCall
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
    fun everyNewCauseInGermanAndEnglish() {
        val cases = listOf(
            Cause.ReleaseEarly(12) to ("Bremse 12 m früher gelöst – länger mit in die Kurve nehmen" to "brake released 12 m earlier – carry it further into the corner"),
            Cause.ReleaseLate(12) to ("Bremse 12 m später gelöst – früher lösen" to "brake released 12 m later – release earlier"),
            Cause.ReleaseAbrupt(100, 400) to ("Bremse abrupt gelöst (0,1 s statt 0,4 s) – weicher auslaufen lassen" to
                "brake released abruptly (0.1 s instead of 0.4 s) – ease off more gently"),
            Cause.LockUp to ("Vorderräder blockieren beim Bremsen" to "front wheels lock under braking"),
            Cause.Understeer to ("Untersteuern am Eingang – später einlenken oder mehr Tempo abbauen" to
                "understeer on entry – turn in later or scrub off more speed"),
            Cause.GripUnused(1.3f, 1.5f) to ("1,3 g statt 1,5 g am Scheitel – Grip nicht ausgenutzt" to "1.3 g instead of 1.5 g at the apex – grip left unused"),
            Cause.ThrottleOnLock(70, 40) to ("Vollgas bei 70 % Lenkung (Vergleich 40 %) – erst Lenkung öffnen" to
                "full throttle at 70 % steering (reference 40 %) – unwind the wheel first"),
            Cause.ThrottleLate(20, 50) to ("Gas erst bei 20 % Lenkung (Vergleich 50 %) – früher ans Gas" to
                "throttle only at 20 % steering (reference 50 %) – get on the throttle earlier"),
            Cause.Oversteer(true) to ("Übersteuern am Ausgang (gegengelenkt) – Gas sanfter" to "oversteer on exit (countersteered) – smoother on the throttle"),
            Cause.Oversteer(false) to ("Übersteuern am Ausgang – Gas sanfter" to "oversteer on exit – smoother on the throttle"),
            Cause.Wheelspin to ("Räder drehen durch am Ausgang" to "wheelspin on exit"),
        )
        for ((cause, texts) in cases) {
            assertEquals("Kurve 4: ${texts.first} – kostet 0,21 s", EngineerText.tip(report(4, 210, cause), DE))
            assertEquals("Turn 4: ${texts.second} – costs 0.21 s", EngineerText.tip(report(4, 210, cause), EN))
        }
    }

    @Test
    fun spokenWritesOutNewUnits() {
        assertEquals(
            "Kurve 4: Bremse abrupt gelöst (0,1 Sekunden statt 0,4 Sekunden) – weicher auslaufen lassen",
            EngineerText.spoken(report(4, 210, Cause.ReleaseAbrupt(100, 400)), DE),
        )
        assertEquals(
            "Kurve 4: Vollgas bei 70 Prozent Lenkung (Vergleich 40 Prozent) – erst Lenkung öffnen",
            EngineerText.spoken(report(4, 210, Cause.ThrottleOnLock(70, 40)), DE),
        )
        assertEquals("Kurve 4: 1,3 G statt 1,5 G am Scheitel – Grip nicht ausgenutzt", EngineerText.spoken(report(4, 210, Cause.GripUnused(1.3f, 1.5f)), DE))
        assertEquals(
            "Turn 4: full throttle at 70 percent steering (reference 40 percent) – unwind the wheel first",
            EngineerText.spoken(report(4, 210, Cause.ThrottleOnLock(70, 40)), EN),
        )
        assertEquals(
            "Turn 4: brake released abruptly (0.1 seconds instead of 0.4 seconds) – ease off more gently",
            EngineerText.spoken(report(4, 210, Cause.ReleaseAbrupt(100, 400)), EN),
        )
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

    @Test
    fun speechSampleIsALapCall() {
        assertEquals("1 Minute 57,0 – 0,3 Sekunden schneller. Noch 3 Runden.", EngineerText.speechSample(DE))
        assertEquals("1 minute 57.0 – 0.3 seconds faster. 3 laps to go.", EngineerText.speechSample(EN))
    }

    @Test
    fun spokenLapCalls() {
        val race = RaceCall.LapDone(117_040, true, -300, 3, 0.43f)
        assertEquals("1 Minute 57,0 – 0,3 Sekunden schneller. Noch 3 Runden. Sprit fehlt für 0,4 Runden – Sprit sparen.", EngineerText.spoken(race, DE))
        assertEquals("1 minute 57.0 – 0.3 seconds faster. 3 laps to go. Fuel short by 0.4 laps – save fuel.", EngineerText.spoken(race, EN))
        assertEquals("2 Minuten 3,5 – 1,2 Sekunden langsamer. Letzte Runde.", EngineerText.spoken(RaceCall.LapDone(123_456, true, 1_200, 1, null), DE))
        assertEquals("58,2 Sekunden – gleich schnell wie die Referenz.", EngineerText.spoken(RaceCall.LapDone(58_200, true, 30, null, null), DE))
        assertEquals("1 Minute 0,0, ungültig.", EngineerText.spoken(RaceCall.LapDone(59_990, false, null, null, null), DE))
        assertEquals("1 Minute 30,0.", EngineerText.spoken(RaceCall.LapDone(90_000, true, null, null, null), DE))
    }

    @Test
    fun spokenTyreCalls() {
        assertEquals("Vorderreifen überhitzen – sanfter einlenken.", EngineerText.spoken(RaceCall.TiresHot(Axle.Front), DE))
        assertEquals("Rear tyres overheating – easier on the throttle.", EngineerText.spoken(RaceCall.TiresHot(Axle.Rear), EN))
        assertEquals("Reifen noch kalt – vorsichtig bremsen.", EngineerText.spoken(RaceCall.TiresCold, DE))
    }
}
