package de.bavaco.gt7.ui

import de.bavaco.gt7.session.Debrief
import de.bavaco.gt7.storage.ComparisonBasis
import de.bavaco.gt7.storage.ComparisonLap
import de.bavaco.gt7.storage.LapSummary
import de.bavaco.gt7.storage.SessionSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class CockpitHeaderTest {
    private val session = SessionSummary(1, 4, "Sugo IRC", 3656, 0L, 85_785, 1, "Max")
    private val lapA = LapSummary(14, 1, 1, 90_543, 2f, true)
    private val lapB = LapSummary(17, 1, 2, 85_785, 2f, true)
    private val stats = LapTableStats(emptyMap(), emptyMap(), listOf(28_000, 29_000, 28_216), 85_216)
    private val car: (Int) -> String = { "Xiaomi VGT" }
    private val ready = EngineerState.Ready(Debrief(emptyList(), emptyList(), 4_700), null, 85_785)

    private fun HeaderCard.short() = "$label|$value|$detail|$tone"

    @Test
    fun pickedLapB() {
        val cards = cockpitHeader(lapA, session, lapB, session, ready, stats, car)
        assertEquals(
            listOf(
                "Runde A|1:30.543|Max · Xiaomi VGT|LapA",
                "Runde B|1:25.785|Max · Xiaomi VGT|LapB",
                "Differenz|+4.758|null|Loss",
                "Theoretisch|1:25.216|null|Best",
            ),
            cards.map { it.short() },
        )
    }

    @Test
    fun automaticComparisonLap() {
        val auto = EngineerState.Ready(Debrief(emptyList(), emptyList(), 4_700), ComparisonLap(17, 85_785, "Max", ComparisonBasis.REFERENCE), null)
        val cards = cockpitHeader(lapA, session, null, null, auto, stats, car)
        assertEquals("Vergleich|1:25.785|Max · Referenzrunde|LapB", cards[1].short())
        assertEquals("Differenz|+4.758|null|Loss", cards[2].short())
    }

    @Test
    fun noComparison() {
        val cards = cockpitHeader(lapA, session, null, null, EngineerState.NoTrack, null, car)
        assertEquals("Vergleich|–|null|Neutral", cards[1].short())
        assertEquals("Differenz|–|null|Neutral", cards[2].short())
        assertEquals("Theoretisch|–|null|Neutral", cards[3].short())
        assertEquals(4, cards.size)
    }

    @Test
    fun fasterLapAGains() {
        val slowB = lapB.copy(timeMs = 91_743)
        assertEquals("Differenz|−1.200|null|Gain", cockpitHeader(lapA, session, slowB, session, ready, stats, car)[2].short())
    }

    @Test
    fun stillComputing() {
        assertEquals("Vergleich|…|null|Neutral", cockpitHeader(lapA, session, null, null, EngineerState.Computing, stats, car)[1].short())
    }

    @Test
    fun theoreticalBestComesFromLapAsSessionEvenWhenAnotherSessionIsOpen() {
        val openLaps = listOf(LapSummary(30, 2, 1, 80_000, 1f, true), LapSummary(31, 2, 2, 81_000, 1f, true))
        val ownLaps = listOf(lapA, LapSummary(15, 1, 2, 91_000, 1f, true))
        var loaded: Long? = null
        assertEquals(ownLaps, sessionLapsOfA(lapA, openLaps) { loaded = it; ownLaps })
        assertEquals(1L, loaded)
        // A's own session is open: no extra loading
        assertEquals(ownLaps, sessionLapsOfA(lapA, ownLaps) { error("must not load") })
    }

    @Test
    fun mapRowTakesWhatIsLeftAboveTheDeltaChart() {
        // 1280 × 800 window minus title bar and tab row leaves about 724 dp for the cockpit
        assertEquals(344f, mapRowHeight(724f))
        assertEquals(460f, mapRowHeight(1200f))
        assertEquals(240f, mapRowHeight(400f))
    }
}
