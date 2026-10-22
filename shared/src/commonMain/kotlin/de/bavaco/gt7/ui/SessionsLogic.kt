package de.bavaco.gt7.ui

import de.bavaco.gt7.session.LapSample
import de.bavaco.gt7.session.scaleSectors
import de.bavaco.gt7.session.sectorBoundaries
import de.bavaco.gt7.session.sectorTimes
import de.bavaco.gt7.session.theoreticalBest
import de.bavaco.gt7.session.vmaxKmh
import de.bavaco.gt7.storage.LapSummary
import de.bavaco.gt7.storage.SessionSummary

enum class SessionColumn(val title: String, val naturallyDescending: Boolean) {
    Track("Strecke", false), Driver("Fahrer", false), Car("Auto", false), Date("Datum", true),
    Laps("Runden", true), Best("Bestzeit", false), Duration("Dauer", true),
}

data class SessionSort(val column: SessionColumn = SessionColumn.Date, val descending: Boolean = true) {
    /** Clicking the sorted column flips it; another column starts in its natural direction. */
    fun toggle(clicked: SessionColumn) = if (clicked == column) copy(descending = !descending) else SessionSort(clicked, clicked.naturallyDescending)
}

sealed interface TrackFilter {
    data object All : TrackFilter
    data object None : TrackFilter
    data class Of(val trackId: Long) : TrackFilter
}

/** [query]: words that must all appear in the session's name, track, car or driver, ignoring case. */
data class SessionFilter(val track: TrackFilter = TrackFilter.All, val carCode: Int? = null, val query: String = "")

private fun matches(s: SessionSummary, words: List<String>, carName: (Int) -> String): Boolean {
    if (words.isEmpty()) return true
    val text = listOfNotNull(s.name, s.trackName, carName(s.carCode), s.driverName).joinToString(" ").lowercase()
    return words.all { it in text }
}

/**
 * [sessions] filtered and sorted for the overview. Missing values (no track, no valid lap, not ended) stay last in
 * both directions; ties go to the newer session.
 */
fun sessionRows(sessions: List<SessionSummary>, filter: SessionFilter, sort: SessionSort, carName: (Int) -> String): List<SessionSummary> {
    val words = filter.query.lowercase().split(' ', '\t').filter { it.isNotBlank() }
    val shown = sessions.filter { s ->
        when (val t = filter.track) {
            TrackFilter.All -> true
            TrackFilter.None -> s.trackId == null
            is TrackFilter.Of -> s.trackId == t.trackId
        } && (filter.carCode == null || s.carCode == filter.carCode) && matches(s, words, carName)
    }
    val key: (SessionSummary) -> Comparable<*>? = when (sort.column) {
        SessionColumn.Track -> { s -> s.trackName?.lowercase() }
        SessionColumn.Driver -> { s -> s.driverName?.lowercase() }
        SessionColumn.Car -> { s -> carName(s.carCode).lowercase() }
        SessionColumn.Date -> { s -> s.startedAtMs }
        SessionColumn.Laps -> { s -> s.lapCount }
        SessionColumn.Best -> { s -> s.bestLapMs }
        SessionColumn.Duration -> { s -> durationMs(s) }
    }
    val byKey = compareBy<SessionSummary> { key(it) == null }
        .then(if (sort.descending) compareByDescending(key) else compareBy(key))
    return shown.sortedWith(byKey.thenByDescending { it.startedAtMs })
}

private fun durationMs(s: SessionSummary): Long? = s.endedAtMs?.let { it - s.startedAtMs }

/** `15:00` (minutes:seconds) of a finished session, `–` while it runs. */
fun formatDuration(s: SessionSummary): String {
    val total = durationMs(s)?.div(1000) ?: return "–"
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/** Per lap Vmax and sector times; best sector times and theoretical best from the valid laps only. */
data class LapTableStats(
    val vmaxKmh: Map<Long, Float?>,
    val sectors: Map<Long, List<Int>?>,
    val bestSectors: List<Int>?,
    val theoreticalMs: Int?,
)

/** Sector boundaries come from the session's fastest valid lap, so every lap of the session uses the same ones. */
fun lapTableStats(laps: List<LapSummary>, samples: Map<Long, List<LapSample>>): LapTableStats {
    val fastest = laps.filter { it.isValid }.minByOrNull { it.timeMs }
    val length = (fastest ?: laps.firstOrNull())?.let { samples[it.id]?.lastOrNull()?.distanceM }
    val boundaries = length?.let { sectorBoundaries(it) }
    val sectors = laps.associate { lap -> lap.id to boundaries?.let { b -> samples[lap.id]?.let { sectorTimes(it, b) } }?.let { scaleSectors(it, lap.timeMs) } }
    val valid = laps.filter { it.isValid }.mapNotNull { sectors[it.id] }
    return LapTableStats(
        vmaxKmh = laps.associate { it.id to samples[it.id]?.let { s -> vmaxKmh(s) } },
        sectors = sectors,
        bestSectors = valid.takeIf { it.isNotEmpty() }?.let { v -> v.first().indices.map { i -> v.minOf { it[i] } } },
        theoreticalMs = theoreticalBest(valid),
    )
}
