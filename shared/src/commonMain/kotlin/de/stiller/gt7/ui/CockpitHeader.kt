package de.stiller.gt7.ui

import de.stiller.gt7.storage.LapSummary
import de.stiller.gt7.storage.SessionSummary

/** Color role of a header card's value. */
enum class Tone { LapA, LapB, Loss, Gain, Best, Neutral }

data class HeaderCard(val label: String, val value: String, val detail: String?, val tone: Tone)

private const val NONE = "–"

private fun who(session: SessionSummary?, carName: (Int) -> String): String? =
    session?.let { listOfNotNull(it.driverName, carName(it.carCode)).joinToString(" · ") }

/**
 * The four cards above the analysis: lap A, lap B or the automatic comparison lap, the difference of their lap times
 * and the theoretical best of A's session.
 */
fun cockpitHeader(
    lapA: LapSummary,
    sessionA: SessionSummary?,
    pickedB: LapSummary?,
    sessionB: SessionSummary?,
    engineer: EngineerState,
    stats: LapTableStats?,
    carName: (Int) -> String,
): List<HeaderCard> {
    val comparison = (engineer as? EngineerState.Ready)?.comparison
    val second = when {
        pickedB != null -> HeaderCard("Runde B", formatLapTime(pickedB.timeMs), who(sessionB, carName), Tone.LapB)
        comparison != null -> HeaderCard(
            "Vergleich", formatLapTime(comparison.timeMs),
            listOfNotNull(comparison.driverName, EngineerText.basisName(comparison.basis, TipLanguage.DE)).joinToString(" · "), Tone.LapB,
        )
        engineer == EngineerState.Computing -> HeaderCard("Vergleich", "…", null, Tone.Neutral)
        else -> HeaderCard("Vergleich", NONE, null, Tone.Neutral)
    }
    val otherMs = pickedB?.timeMs ?: comparison?.timeMs
    val difference = otherMs?.let { lapA.timeMs - it }
    return listOf(
        HeaderCard("Runde A", formatLapTime(lapA.timeMs), who(sessionA, carName), Tone.LapA),
        second,
        HeaderCard(
            "Differenz", difference?.let { formatDelta(it) } ?: NONE, null,
            when { difference == null -> Tone.Neutral; difference > 0 -> Tone.Loss; difference < 0 -> Tone.Gain; else -> Tone.Neutral },
        ),
        stats?.theoreticalMs?.let { HeaderCard("Theoretisch", formatLapTime(it), null, Tone.Best) }
            ?: HeaderCard("Theoretisch", NONE, null, Tone.Neutral),
    )
}

/** Laps of lap A's session: the open session's laps when A comes from it, else [load]ed — B may come from another session. */
fun sessionLapsOfA(lapA: LapSummary, openLaps: List<LapSummary>, load: (Long) -> List<LapSummary>): List<LapSummary> =
    if (openLaps.any { it.sessionId == lapA.sessionId }) openLaps else load(lapA.sessionId)

/** Space the cockpit keeps for padding, toolbar, header cards, delta chart and gaps, so the delta chart stays in view. */
private const val RESERVED_DP = 380f

/** Height (dp) of the map and corner-list row for a cockpit [availableDp] high: what is left, within sensible bounds. */
fun mapRowHeight(availableDp: Float): Float = (availableDp - RESERVED_DP).coerceIn(240f, 460f)
