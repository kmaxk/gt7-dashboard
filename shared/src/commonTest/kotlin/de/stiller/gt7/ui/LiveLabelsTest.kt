package de.stiller.gt7.ui

import de.stiller.gt7.session.Opponent
import de.stiller.gt7.storage.Driver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveLabelsTest {
    @Test fun selfKeepsClassicLabels() {
        assertEquals(
            OpponentLabels("BESTE", "DELTA", "ABSTAND ZU DEINER BESTZEIT", "Max"),
            opponentLabels(Opponent.Self, "Max"),
        )
    }

    @Test fun otherDriverNamesTheOpponent() {
        assertEquals(
            OpponentLabels("ANNA", "VS. ANNA", "ABSTAND ZU ANNA", "Anna"),
            opponentLabels(Opponent.Of(Driver(2, "Anna")), "Max"),
        )
    }

    @Test fun lapPathRestartsOnNewLapAndIsCapped() {
        val path = LapPath(maxPoints = 3)
        path.add(100, 1f, 1f); path.add(200, 2f, 2f); path.add(300, 3f, 3f); path.add(400, 4f, 4f)
        assertEquals(listOf(2f to 2f, 3f to 3f, 4f to 4f), path.points())
        path.add(16, 9f, 9f) // lap time dropped: new lap
        assertEquals(listOf(9f to 9f), path.points())
    }

    @Test fun deltaHistoryLooksBackByPackets() {
        val h = DeltaHistory(lookBackPackets = 60)
        for (id in 1..100) h.add(id, id * 16, id * 10)
        assertEquals(400, h.before(100)) // packet 40
        assertNull(DeltaHistory(60).apply { add(1, 16, 5) }.before(1))
    }

    @Test fun deltaHistoryToleratesLostPackets() {
        val h = DeltaHistory(lookBackPackets = 60)
        for (id in 1..100) if (id != 40) h.add(id, id * 16, id * 10)
        assertEquals(390, h.before(100)) // packet 40 lost: nearest older one
    }

    @Test fun deltaHistoryRestartsOnNewLap() {
        val h = DeltaHistory(lookBackPackets = 60)
        for (id in 1..100) h.add(id, 50_000 + id * 16, 800)
        h.add(101, 16, 0) // crossed the line: lap time dropped
        assertNull(h.before(101))
    }

    @Test fun bestCellShowsOwnGt7BestOnlyAgainstSelf() {
        assertEquals(106_874, bestCellTimeMs(null, Opponent.Self, 106_874))
        assertNull(bestCellTimeMs(null, Opponent.Of(Driver(2, "Anna")), 106_874))
        assertNull(bestCellTimeMs(null, Opponent.Self, 0))
    }
}
