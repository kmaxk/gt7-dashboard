package de.stiller.gt7.ui

import de.stiller.gt7.storage.SessionSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionsLogicTest {
    private fun s(id: Long, track: String?, car: Int, start: Long, best: Int?, driver: String = "Max", laps: Int = 3, end: Long? = null) =
        SessionSummary(id, track?.let { it.length.toLong() }, track, car, start, best, 1, driver, laps, end)

    private val spa = s(1, "Spa", 24, 3_000, 140_000, "Anna", laps = 5, end = 3_000 + 900_000)
    private val monza = s(2, "Monza", 3332, 1_000, 101_000, "max", laps = 2, end = 1_000 + 300_000)
    private val none = s(3, null, 24, 2_000, null, "Bert", laps = 1)
    private val spa2 = s(4, "Spa", 3332, 4_000, 139_000, laps = 7, end = 4_000 + 600_000)
    private val all = listOf(spa, monza, none, spa2)
    private val car: (Int) -> String = { if (it == 24) "180SX" else "L500R" }

    private fun ids(filter: SessionFilter = SessionFilter(), sort: SessionSort = SessionSort()) = sessionRows(all, filter, sort, car).map { it.id }

    @Test fun defaultIsNewestFirst() = assertEquals(listOf(4L, 1, 3, 2), ids())

    @Test fun sortsEachColumnBothWays() {
        assertEquals(listOf(2L, 4, 1, 3), ids(sort = SessionSort(SessionColumn.Track, descending = false))) // ties: newest first
        assertEquals(listOf(4L, 1, 2, 3), ids(sort = SessionSort(SessionColumn.Track, descending = true)))
        assertEquals(listOf(1L, 3, 4, 2), ids(sort = SessionSort(SessionColumn.Driver, descending = false))) // Anna, Bert, Max/max ignoring case
        assertEquals(listOf(1L, 3, 4, 2), ids(sort = SessionSort(SessionColumn.Car, descending = false))) // by name: 180SX, then L500R
        assertEquals(listOf(2L, 3, 1, 4), ids(sort = SessionSort(SessionColumn.Date, descending = false)))
        assertEquals(listOf(4L, 1, 2, 3), ids(sort = SessionSort(SessionColumn.Laps, descending = true)))
        assertEquals(listOf(3L, 2, 1, 4), ids(sort = SessionSort(SessionColumn.Laps, descending = false)))
        assertEquals(listOf(2L, 4, 1, 3), ids(sort = SessionSort(SessionColumn.Best, descending = false)))
        assertEquals(listOf(1L, 4, 2, 3), ids(sort = SessionSort(SessionColumn.Best, descending = true)))
        assertEquals(listOf(1L, 4, 2, 3), ids(sort = SessionSort(SessionColumn.Duration, descending = true)))
    }

    @Test fun missingValuesStayLast() {
        // no track, no best lap, no end: last in both directions
        assertEquals(3L, ids(sort = SessionSort(SessionColumn.Track, descending = false)).last())
        assertEquals(3L, ids(sort = SessionSort(SessionColumn.Best, descending = true)).last())
        assertEquals(3L, ids(sort = SessionSort(SessionColumn.Duration, descending = false)).last())
    }

    @Test fun filtersByTrackCarAndBoth() {
        assertEquals(listOf(4L, 1), ids(SessionFilter(track = TrackFilter.Of(3))))
        assertEquals(listOf(3L), ids(SessionFilter(track = TrackFilter.None)))
        assertEquals(listOf(1L, 3), ids(SessionFilter(carCode = 24)))
        assertEquals(listOf(1L), ids(SessionFilter(track = TrackFilter.Of(3), carCode = 24)))
    }

    @Test fun clickingTheSameColumnFlipsAnotherStartsWithItsNaturalDirection() {
        assertEquals(SessionSort(SessionColumn.Date, descending = false), SessionSort().toggle(SessionColumn.Date))
        assertEquals(SessionSort(SessionColumn.Track, descending = false), SessionSort().toggle(SessionColumn.Track))
        assertEquals(SessionSort(SessionColumn.Best, descending = false), SessionSort().toggle(SessionColumn.Best))
        assertEquals(SessionSort(SessionColumn.Laps, descending = true), SessionSort().toggle(SessionColumn.Laps))
    }

    @Test fun durationIsMinutesAndSeconds() {
        assertEquals("15:00", formatDuration(spa))
        assertEquals("–", formatDuration(none))
    }

    @Test fun searchMatchesNameTrackCarAndDriverIgnoringCase() {
        val named = spa.copy(name = "Quali-Training")
        val list = listOf(named, monza, none, spa2)
        fun found(q: String) = sessionRows(list, SessionFilter(query = q), SessionSort(), car).map { it.id }
        assertEquals(listOf(1L), found("quali"))
        assertEquals(listOf(4L, 1), found("SPA"))
        assertEquals(listOf(1L, 3), found("180sx"))
        assertEquals(listOf(3L), found("bert"))
        // every word has to match somewhere
        assertEquals(listOf(4L), found("spa l500"))
        assertEquals(listOf(4L, 1, 3, 2), found("  "))
    }

    @Test fun searchAddsToTheOtherFilters() {
        assertEquals(listOf(4L), sessionRows(all, SessionFilter(carCode = 3332, query = "spa"), SessionSort(), car).map { it.id })
    }

    /** 300 m in 10 m steps; [msPer10m] per third. */
    private fun lapSamples(vararg msPer10m: Int, speed: Float = 30f): List<de.stiller.gt7.session.LapSample> {
        var t = 0
        return (0..30).map { i ->
            if (i > 0) t += msPer10m[(i - 1) / 10]
            de.stiller.gt7.session.LapSample(t, i * 10f, speed + i, 0f, 0f, 3, 0f, 0f, 0f, 0f)
        }
    }

    @Test fun lapTableStatsUseTheSessionsFastestLapForSectorsAndOnlyValidLapsForBests() {
        val laps = listOf(
            de.stiller.gt7.storage.LapSummary(1, 1, 1, 30_000, 1f, true),
            de.stiller.gt7.storage.LapSummary(2, 1, 2, 29_000, 1f, true),
            de.stiller.gt7.storage.LapSummary(3, 1, 3, 20_000, 1f, false),
        )
        val samples = mapOf(
            1L to lapSamples(900, 1000, 1100),
            2L to lapSamples(1000, 1000, 900),
            3L to lapSamples(500, 500, 1000),
        )
        val stats = lapTableStats(laps, samples)
        assertEquals(listOf(9_000, 10_000, 11_000), stats.sectors[1L])
        assertEquals(listOf(5_000, 5_000, 10_000), stats.sectors[3L])
        assertEquals(listOf(9_000, 10_000, 9_000), stats.bestSectors)
        assertEquals(28_000, stats.theoreticalMs)
        assertEquals(60f * 3.6f, stats.vmaxKmh[2L]!!, 1e-3f)
    }

    @Test fun lapTableStatsWithoutValidLaps() {
        val stats = lapTableStats(listOf(de.stiller.gt7.storage.LapSummary(1, 1, 1, 30_000, 1f, false)), mapOf(1L to lapSamples(1000, 1000, 1000)))
        assertEquals(null, stats.theoreticalMs)
        assertEquals(null, stats.bestSectors)
    }
}
