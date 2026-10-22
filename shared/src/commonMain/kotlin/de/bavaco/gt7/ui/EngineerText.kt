package de.bavaco.gt7.ui

import de.bavaco.gt7.session.Cause
import de.bavaco.gt7.session.CornerReport
import de.bavaco.gt7.storage.ComparisonBasis
import de.bavaco.gt7.storage.ComparisonLap
import kotlin.math.abs
import kotlin.math.roundToInt

/** Language of the race engineer's tips; the rest of the app stays German. */
enum class TipLanguage { DE, EN }

const val TIP_LANGUAGE_SETTING = "tipLanguage"

fun tipLanguageOf(setting: String?): TipLanguage = TipLanguage.entries.firstOrNull { it.name == setting } ?: TipLanguage.DE

/** Sentences of the race engineer in German or English. */
object EngineerText {
    private fun de(l: TipLanguage) = l == TipLanguage.DE

    fun corner(number: Int, l: TipLanguage) = if (de(l)) "Kurve $number" else "Turn $number"

    /** `Kurve 4: 14 m später bremsen – kostet 0,21 s (auch: Gang 2 statt 3)`. */
    fun tip(r: CornerReport, l: TipLanguage): String {
        val main = r.causes.firstOrNull()?.let { cause(it, l) }
            ?: if (de(l)) "Zeitverlust ohne klare Ursache, Linie prüfen" else "time lost without a clear cause, check your line"
        val cost = if (de(l)) "kostet" else "costs"
        val more = r.causes.drop(1).takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = if (de(l)) " (auch: " else " (also: ", postfix = ")") { cause(it, l) } ?: ""
        return "${corner(r.corner.number, l)}: $main – $cost ${seconds(r.lossMs, l)}$more"
    }

    /** `Kurve 4: 14 m später bremsen`, the main cause only, for the live banner. */
    fun liveTip(r: CornerReport, l: TipLanguage): String {
        val main = r.causes.firstOrNull()?.let { cause(it, l) } ?: if (de(l)) "Linie prüfen" else "check your line"
        return "${corner(r.corner.number, l)}: $main"
    }

    /** [liveTip] with units written out so a voice reads them: `Kurve 4: 14 Meter später bremsen`. */
    fun spoken(r: CornerReport, l: TipLanguage): String =
        liveTip(r, l)
            .replace(Regex("(\\d+) m\\b"), if (de(l)) "$1 Meter" else "$1 meters")
            .replace("km/h", if (de(l)) "Kilometer pro Stunde" else "kilometers per hour")

    fun cause(c: Cause, l: TipLanguage): String = when (c) {
        is Cause.BrakeEarly -> if (de(l)) "${c.meters} m später bremsen" else "brake ${c.meters} m later"
        is Cause.BrakeLate -> if (de(l)) "${c.meters} m früher bremsen" else "brake ${c.meters} m earlier"
        is Cause.SlowApex -> if (de(l)) "${c.kmh} km/h mehr Scheitelgeschwindigkeit" else "carry ${c.kmh} km/h more through the apex"
        is Cause.OverspeedApex ->
            if (de(l)) "${c.kmh} km/h zu schnell am Scheitel – langsamer rein, früher raus" else "${c.kmh} km/h too fast at the apex – slow in, fast out"
        is Cause.LateThrottle ->
            if (de(l)) "früher ans Gas, ${c.meters} m später als die Vergleichsrunde" else "get on the throttle earlier, ${c.meters} m later than the reference"
        is Cause.GearDiffers -> if (de(l)) "Gang ${c.lap} statt ${c.reference}" else "gear ${c.lap} instead of ${c.reference}"
    }

    /** `verglichen mit: Max · 1:42.318 · gleiches Auto`. */
    fun basis(lap: ComparisonLap, l: TipLanguage): String {
        val prefix = if (de(l)) "verglichen mit" else "compared with"
        return "$prefix: ${lap.driverName ?: "?"} · ${formatLapTime(lap.timeMs)} · ${basisName(lap.basis, l)}"
    }

    /** Why the comparison lap was chosen, e.g. `Referenzrunde`. */
    fun basisName(basis: ComparisonBasis, l: TipLanguage): String = when (basis) {
        ComparisonBasis.REFERENCE -> if (de(l)) "Referenzrunde" else "reference lap"
        ComparisonBasis.SAME_CAR -> if (de(l)) "gleiches Auto" else "same car"
        ComparisonBasis.SAME_DRIVER -> if (de(l)) "anderes Auto" else "other car"
        ComparisonBasis.ANY -> if (de(l)) "schnellste auf der Strecke" else "fastest on the track"
    }

    /** Basis when the user picked lap B: `verglichen mit: Runde B · 1:42.318`. */
    fun pickedBasis(timeMs: Int, l: TipLanguage): String =
        if (de(l)) "verglichen mit: Runde B · ${formatLapTime(timeMs)}" else "compared with: lap B · ${formatLapTime(timeMs)}"

    /** Seconds with two decimals and the language's decimal mark, e.g. `0,21 s`; [signed] adds + or −. */
    fun seconds(ms: Int, l: TipLanguage, signed: Boolean = false): String {
        val text = formatDecimal(abs(ms) / 1000f, 2).let { if (de(l)) it.replace('.', ',') else it }
        val sign = if (!signed) "" else if (ms < 0) "−" else "+"
        return "$sign$text s"
    }

    fun title(l: TipLanguage) = if (de(l)) "Renningenieur" else "Race engineer"
    fun total(ms: Int, l: TipLanguage) = (if (de(l)) "Gesamt: " else "Total: ") + seconds(ms, l, signed = true)
    fun allGood(l: TipLanguage) = if (de(l)) "Stark – überall gleich schnell oder schneller." else "Strong – as fast or faster everywhere."
    fun noComparison(l: TipLanguage) =
        if (de(l)) "Keine Vergleichsrunde – fahr eine zweite Runde oder wähle zwei Runden." else "No comparison lap – drive another lap or pick two laps."
    fun noTrack(l: TipLanguage) =
        if (de(l)) "Runde ohne Strecke – wähle zwei Runden zum Vergleich." else "Lap without a track – pick two laps to compare."
    fun noCorners(l: TipLanguage) = if (de(l)) "Keine Kurven erkannt." else "No corners found."
    fun computing(l: TipLanguage) = if (de(l)) "Rechne…" else "Working…"
}

/** Lap position minus comparison position in whole meters, `+14 m` = later; `–` if either is missing. */
fun meterDiff(own: Float?, ref: Float?): String {
    if (own == null || ref == null) return "–"
    val d = (own - ref).roundToInt()
    return when {
        d > 0 -> "+$d m"
        d < 0 -> "−${-d} m"
        else -> "±0 m"
    }
}
