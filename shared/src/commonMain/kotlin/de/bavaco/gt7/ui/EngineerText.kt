package de.bavaco.gt7.ui

import de.bavaco.gt7.session.Axle
import de.bavaco.gt7.session.Cause
import de.bavaco.gt7.session.CornerReport
import de.bavaco.gt7.session.RaceCall
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
            .replace(Regex("(\\d) %"), if (de(l)) "$1 Prozent" else "$1 percent")
            .replace(Regex("(\\d) s\\b"), if (de(l)) "$1 Sekunden" else "$1 seconds")
            .replace(Regex("(\\d) g\\b"), if (de(l)) "$1 G" else "$1 g")

    /** What the settings play to try a voice: a typical end-of-lap call. */
    fun speechSample(l: TipLanguage): String = spoken(RaceCall.LapDone(117_040, true, -300, 3, null), l)

    /** A race call as one spoken announcement, e.g. `1 Minute 57,0 – 0,3 Sekunden schneller. Noch 3 Runden.` */
    fun spoken(call: RaceCall, l: TipLanguage): String = when (call) {
        is RaceCall.LapDone -> buildList {
            val time = spokenLapTime(call.timeMs, l)
            add(
                when {
                    !call.valid -> if (de(l)) "$time, ungültig" else "$time, invalid"
                    call.deltaMs == null -> time
                    else -> "$time – ${spokenDelta(call.deltaMs, l)}"
                },
            )
            call.lapsLeft?.let {
                add(if (it == 1) (if (de(l)) "Letzte Runde" else "Final lap") else if (de(l)) "Noch $it Runden" else "$it laps to go")
            }
            call.fuelShortLaps?.let {
                val laps = tenths(it, l)
                add(if (de(l)) "Sprit fehlt für $laps Runden – Sprit sparen" else "Fuel short by $laps laps – save fuel")
            }
        }.joinToString(". ", postfix = ".")
        is RaceCall.TiresHot -> when (call.axle) {
            Axle.Front -> if (de(l)) "Vorderreifen überhitzen – sanfter einlenken." else "Front tyres overheating – turn in more gently."
            Axle.Rear -> if (de(l)) "Hinterreifen überhitzen – sanfter ans Gas." else "Rear tyres overheating – easier on the throttle."
            Axle.Both -> if (de(l)) "Reifen überhitzen – etwas Tempo rausnehmen." else "Tyres overheating – back off a little."
        }
        RaceCall.TiresCold -> if (de(l)) "Reifen noch kalt – vorsichtig bremsen." else "Tyres still cold – brake gently."
    }

    /** `1 Minute 57,0`, `2 Minuten 3,4`, `58,2 Sekunden`: tenths, units written out for the voice. */
    private fun spokenLapTime(ms: Int, l: TipLanguage): String {
        val totalTenths = (ms + 50) / 100
        val minutes = totalTenths / 600
        val seconds = tenths((totalTenths % 600) / 10f, l)
        if (minutes == 0) return if (de(l)) "$seconds Sekunden" else "$seconds seconds"
        val unit = if (de(l)) (if (minutes == 1) "Minute" else "Minuten") else if (minutes == 1) "minute" else "minutes"
        return "$minutes $unit $seconds"
    }

    private fun spokenDelta(ms: Int, l: TipLanguage): String {
        if (abs(ms) < 50) return if (de(l)) "gleich schnell wie die Referenz" else "level with the reference"
        val s = tenths(abs(ms) / 1000f, l)
        return if (ms < 0) (if (de(l)) "$s Sekunden schneller" else "$s seconds faster") else if (de(l)) "$s Sekunden langsamer" else "$s seconds slower"
    }

    private fun tenths(value: Float, l: TipLanguage) = formatDecimal(value, 1).let { if (de(l)) it.replace('.', ',') else it }

    fun cause(c: Cause, l: TipLanguage): String = when (c) {
        is Cause.BrakeEarly -> if (de(l)) "${c.meters} m später bremsen" else "brake ${c.meters} m later"
        is Cause.BrakeLate -> if (de(l)) "${c.meters} m früher bremsen" else "brake ${c.meters} m earlier"
        is Cause.SlowApex -> if (de(l)) "${c.kmh} km/h mehr Scheitelgeschwindigkeit" else "carry ${c.kmh} km/h more through the apex"
        is Cause.OverspeedApex ->
            if (de(l)) "${c.kmh} km/h zu schnell am Scheitel – langsamer rein, früher raus" else "${c.kmh} km/h too fast at the apex – slow in, fast out"
        is Cause.LateThrottle ->
            if (de(l)) "früher ans Gas, ${c.meters} m später als die Vergleichsrunde" else "get on the throttle earlier, ${c.meters} m later than the reference"
        is Cause.GearDiffers -> if (de(l)) "Gang ${c.lap} statt ${c.reference}" else "gear ${c.lap} instead of ${c.reference}"
        is Cause.ReleaseEarly ->
            if (de(l)) "Bremse ${c.meters} m früher gelöst – länger mit in die Kurve nehmen" else "brake released ${c.meters} m earlier – carry it further into the corner"
        is Cause.ReleaseLate -> if (de(l)) "Bremse ${c.meters} m später gelöst – früher lösen" else "brake released ${c.meters} m later – release earlier"
        is Cause.ReleaseAbrupt -> {
            val s = tenths(c.ms / 1000f, l)
            val r = tenths(c.refMs / 1000f, l)
            if (de(l)) "Bremse abrupt gelöst ($s s statt $r s) – weicher auslaufen lassen" else "brake released abruptly ($s s instead of $r s) – ease off more gently"
        }
        Cause.LockUp -> if (de(l)) "Vorderräder blockieren beim Bremsen" else "front wheels lock under braking"
        Cause.Understeer ->
            if (de(l)) "Untersteuern am Eingang – später einlenken oder mehr Tempo abbauen" else "understeer on entry – turn in later or scrub off more speed"
        is Cause.GripUnused -> {
            val g = tenths(c.g, l)
            val r = tenths(c.refG, l)
            if (de(l)) "$g g statt $r g am Scheitel – Grip nicht ausgenutzt" else "$g g instead of $r g at the apex – grip left unused"
        }
        is Cause.ThrottleOnLock ->
            if (de(l)) "Vollgas bei ${c.pct} % Lenkung (Vergleich ${c.refPct} %) – erst Lenkung öffnen"
            else "full throttle at ${c.pct} % steering (reference ${c.refPct} %) – unwind the wheel first"
        is Cause.ThrottleLate ->
            if (de(l)) "Gas erst bei ${c.pct} % Lenkung (Vergleich ${c.refPct} %) – früher ans Gas"
            else "throttle only at ${c.pct} % steering (reference ${c.refPct} %) – get on the throttle earlier"
        is Cause.Oversteer -> {
            val how = if (!c.countersteer) "" else if (de(l)) " (gegengelenkt)" else " (countersteered)"
            if (de(l)) "Übersteuern am Ausgang$how – Gas sanfter" else "oversteer on exit$how – smoother on the throttle"
        }
        Cause.Wheelspin -> if (de(l)) "Räder drehen durch am Ausgang" else "wheelspin on exit"
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
