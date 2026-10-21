package de.stiller.gt7.ui

import de.stiller.gt7.storage.Driver
import de.stiller.gt7.storage.LeaderboardEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeaderboardRowsTest {
    private fun entry(id: Long, name: String, timeMs: Int) = LeaderboardEntry(Driver(id, name), timeMs, 1, 0L)

    @Test fun placesAndGapsWithTies() {
        val rows = leaderboardRows(listOf(entry(1, "Max", 99_000), entry(2, "Anna", 100_000), entry(3, "Tom", 100_000)))
        assertEquals(listOf(1, 2, 2), rows.map { it.place })
        assertEquals(listOf(0, 1000, 1000), rows.map { it.gapMs })
        assertEquals(listOf("Max", "Anna", "Tom"), rows.map { it.name })
    }

    @Test fun emptyBoard() {
        assertTrue(leaderboardRows(emptyList()).isEmpty())
    }
}
