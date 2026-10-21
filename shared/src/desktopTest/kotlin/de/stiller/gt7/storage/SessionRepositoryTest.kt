package de.stiller.gt7.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.stiller.gt7.db.Gt7Database
import de.stiller.gt7.session.CompletedLap
import de.stiller.gt7.session.LapSample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionRepositoryTest {
    private fun repo(): SessionRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Gt7Database.Schema.create(driver)
        return SessionRepository(Gt7Database(driver))
    }

    private fun sample(t: Int) = LapSample(t, t * 0.5f, 30f + t, 0.5f, 0.25f, 3, 6000f, t * 1.5f, -t * 2f, 0.75f)

    private fun lap(n: Int, timeMs: Int, valid: Boolean = true, tag: Int = 0) =
        CompletedLap(n, timeMs, timeMs, 4.5f, valid, listOf(sample(tag), sample(tag + 100), sample(tag + 200)))

    @Test fun savesAndReadsLapWithSamples() {
        val r = repo()
        val s = r.createSession(r.ensureDefaultDriver().id, 3332, 1000L)
        val lap = lap(1, 100_000)
        val id = r.saveLap(s, lap)
        val summary = r.laps(s).single()
        assertEquals(LapSummaryExpect(id, s, 1, 100_000, 4.5f, true), LapSummaryExpect(summary.id, summary.sessionId, summary.number, summary.timeMs, summary.fuelUsed, summary.isValid))
        assertEquals(lap.samples, r.samples(id))
    }

    @Test fun bestLapIgnoresInvalidLaps() {
        val r = repo()
        val t = r.createTrack("Ring", floatArrayOf(0f))
        val s = r.createSession(r.ensureDefaultDriver().id, 1, 0L)
        r.assignTrack(s, t)
        val good = lap(1, 100_000, true, 1)
        r.saveLap(s, good)
        r.saveLap(s, lap(2, 90_000, false, 2))
        assertEquals(good.samples, r.bestLap(t, r.ensureDefaultDriver().id, 1)?.samples)
    }

    @Test fun bestLapSpansSessionsOfSameTrack() {
        val r = repo()
        val t = r.createTrack("Ring", floatArrayOf(0f))
        val s1 = r.createSession(r.ensureDefaultDriver().id, 1, 0L)
        val s2 = r.createSession(r.ensureDefaultDriver().id, 1, 5L)
        r.assignTrack(s1, t); r.assignTrack(s2, t)
        val best = lap(1, 95_000, true, 1)
        r.saveLap(s1, best)
        r.saveLap(s2, lap(1, 99_000, true, 2))
        assertEquals(best.samples, r.bestLap(t, r.ensureDefaultDriver().id, 1)?.samples)
        assertEquals(5L, r.lastSessionStartForTrack(t))
        assertNull(r.bestLap(t + 1, r.ensureDefaultDriver().id, 1))
    }

    @Test fun bestLapHasRealTimeAndSamples() {
        val r = repo()
        val t = r.createTrack("Ring", floatArrayOf(0f))
        val s = r.createSession(r.ensureDefaultDriver().id, 1, 0L)
        r.assignTrack(s, t)
        val best = lap(1, 95_123, true, 1)
        r.saveLap(s, lap(2, 99_000, true, 2))
        r.saveLap(s, best)
        r.saveLap(s, lap(3, 90_000, false, 3))
        assertEquals(BestLap(95_123, best.samples, 1, 0L), r.bestLap(t, r.ensureDefaultDriver().id, 1))
        assertNull(r.bestLap(t + 1, r.ensureDefaultDriver().id, 1))
    }

    @Test fun outlineRoundTrips() {
        val r = repo()
        val outline = floatArrayOf(0f, 1.5f, -2.25f, 1e6f)
        val id = r.createTrack("T", outline)
        val track = r.tracks().single()
        assertEquals(id, track.id)
        assertEquals("T", track.name)
        assertContentEquals(outline, track.outline)
    }

    @Test fun sessionsFlowEmitsNewestFirstWithBestLap() = runBlocking {
        val r = repo()
        val t = r.createTrack("Ring", floatArrayOf(0f))
        val old = r.createSession(r.ensureDefaultDriver().id, 1, 100L)
        val new = r.createSession(r.ensureDefaultDriver().id, 2, 200L)
        r.assignTrack(old, t)
        r.saveLap(old, lap(1, 100_000))
        r.saveLap(old, lap(2, 80_000, false))
        r.saveLap(old, lap(3, 98_000))
        val list = r.sessions().first()
        assertEquals(listOf(new, old), list.map { it.id })
        assertNull(list[0].bestLapMs); assertNull(list[0].trackName)
        assertEquals(98_000, list[1].bestLapMs)
        assertEquals("Ring", list[1].trackName)
    }

    @Test fun settingsUpsert() {
        val r = repo()
        assertNull(r.setting("k"))
        r.setSetting("k", "a"); r.setSetting("k", "b")
        assertEquals("b", r.setting("k"))
    }

    private data class LapSummaryExpect(val id: Long, val sessionId: Long, val n: Int, val t: Int, val f: Float, val v: Boolean)

    @Test fun freshDatabaseGetsDefaultDriver() {
        val r = repo()
        val first = r.ensureDefaultDriver()
        assertEquals("Fahrer 1", first.name)
        assertEquals(first.id, r.ensureDefaultDriver().id)
    }

    @Test fun deleteDriverRemovesTheirSessionsAndLaps() {
        val r = repo(); val ring = Ring(r)
        val max = r.createDriver("Max")
        val anna = r.createDriver("Anna")
        ring.drive(max, 1, 100_000)
        ring.drive(anna, 1, 95_000)
        r.deleteDriver(anna)
        assertEquals(listOf("Max"), r.drivers().map { it.name })
        assertEquals(listOf("Max"), runBlocking { r.sessions().first() }.map { it.driverName })
        assertEquals(listOf("Max"), r.leaderboard(ring.track, null).map { it.driver.name })
        assertEquals(setOf(max), r.driversWithSessions())
    }

    @Test fun trackLineIsFastestValidLapOfAnyDriver() {
        val r = repo()
        val t = r.createTrack("Ring", floatArrayOf(0f))
        assertNull(r.trackLine(t))
        fun session(driver: Long) = r.createSession(driver, 1, 0L).also { r.assignTrack(it, t) }
        val max = session(r.createDriver("Max"))
        val anna = session(r.createDriver("Anna"))
        r.saveLap(max, lap(1, 100_000, true, 1))
        r.saveLap(anna, lap(1, 90_000, false, 2))
        val fastest = lap(2, 95_000, true, 3)
        r.saveLap(anna, fastest)
        assertEquals(fastest.samples, r.trackLine(t))
    }

    @Test fun chosenLineLapWinsUntilItIsGone() {
        val r = repo()
        val t = r.createTrack("Ring", floatArrayOf(0f))
        val s = r.createSession(r.createDriver("Anna"), 1, 0L).also { r.assignTrack(it, t) }
        val fast = lap(1, 90_000, true, 1)
        val slow = lap(2, 99_000, true, 2)
        r.saveLap(s, fast)
        val slowId = r.saveLap(s, slow)
        r.setTrackLine(t, slowId)
        assertEquals(slow.samples, r.trackLine(t))
        r.setTrackLine(t, null)
        assertEquals(fast.samples, r.trackLine(t))
        r.setTrackLine(t, 9999L) // lap deleted meanwhile
        assertEquals(fast.samples, r.trackLine(t))
    }

    @Test fun trackSummariesAndLineCandidates() {
        val r = repo(); val ring = Ring(r)
        val anna = r.createDriver("Anna")
        ring.drive(anna, 1, 100_000)
        ring.drive(anna, 1, 95_000)
        ring.drive(anna, 1, 90_000, valid = false)
        val summary = r.trackSummaries().single()
        assertEquals("Ring", summary.name)
        assertEquals(3, summary.lapCount)
        assertEquals(2L, summary.lastDrivenAtMs)
        assertEquals(listOf(95_000, 100_000), r.lineCandidates(ring.track).map { it.timeMs })
        assertEquals("Anna", r.lineCandidates(ring.track).first().driverName)
    }

    @Test fun renameAndDeleteTrackKeepsLaps() {
        val r = repo(); val ring = Ring(r)
        val anna = r.createDriver("Anna")
        val s = ring.drive(anna, 1, 100_000)
        r.renameTrack(ring.track, "Spa")
        assertEquals(listOf("Spa"), r.tracks().map { it.name })
        r.deleteTrack(ring.track)
        assertEquals(emptyList(), r.tracks())
        assertEquals(1, r.laps(s).size)
        assertNull(runBlocking { r.sessions().first() }.single().trackId)
    }

    /** Track "Ring" plus helpers: [drive] saves one lap for [driver] with [car] in a new session. */
    private class Ring(val r: SessionRepository) {
        val track = r.createTrack("Ring", floatArrayOf(0f))
        private var clock = 0L
        fun drive(driver: Long, car: Int, timeMs: Int, valid: Boolean = true): Long {
            val s = r.createSession(driver, car, clock++)
            r.assignTrack(s, track)
            r.saveLap(s, CompletedLap(1, timeMs, timeMs, 4.5f, valid, listOf(LapSample(0, 0f, 0f, 0f, 0f, 1, 0f, 0f, 0f, 0f))))
            return s
        }
    }

    @Test fun bestLapPrefersSameCar() {
        val r = repo(); val ring = Ring(r)
        val anna = r.createDriver("Anna")
        ring.drive(anna, 1, 100_000)
        ring.drive(anna, 2, 95_000)
        val best = r.bestLap(ring.track, anna, 1)
        assertEquals(100_000, best?.timeMs)
        assertEquals(1, best?.carCode)
    }

    @Test fun bestLapFallsBackToOtherCar() {
        val r = repo(); val ring = Ring(r)
        val anna = r.createDriver("Anna")
        ring.drive(anna, 2, 95_000)
        assertEquals(2, r.bestLap(ring.track, anna, 1)?.carCode)
    }

    @Test fun bestLapOnlyOwnDriverAndValid() {
        val r = repo(); val ring = Ring(r)
        val max = r.createDriver("Max"); val anna = r.createDriver("Anna"); val tom = r.createDriver("Tom")
        ring.drive(max, 1, 90_000)
        ring.drive(anna, 1, 99_000)
        ring.drive(anna, 1, 80_000, valid = false)
        assertEquals(99_000, r.bestLap(ring.track, anna, 1)?.timeMs)
        assertNull(r.bestLap(ring.track, tom, 1))
    }

    @Test fun leaderboardOneEntryPerDriver() {
        val r = repo(); val ring = Ring(r)
        val max = r.createDriver("Max"); val anna = r.createDriver("Anna")
        ring.drive(max, 1, 101_000)
        ring.drive(max, 2, 99_000)
        ring.drive(anna, 1, 100_000)
        val all = r.leaderboard(ring.track, null)
        assertEquals(listOf("Max", "Anna"), all.map { it.driver.name })
        assertEquals(listOf(99_000, 100_000), all.map { it.timeMs })
        val car1 = r.leaderboard(ring.track, 1)
        assertEquals(listOf("Anna" to 100_000, "Max" to 101_000), car1.map { it.driver.name to it.timeMs })
        assertEquals(listOf(1, 2), r.carsOnTrack(ring.track))
    }

    @Test fun sessionsCarryDriverName() = kotlinx.coroutines.runBlocking {
        val r = repo()
        val max = r.createDriver("Max")
        r.createSession(max, 1, 0L)
        assertEquals("Max", r.sessions().first().single().driverName)
    }

    @Test fun sessionsCarryLapCountAndEnd() = kotlinx.coroutines.runBlocking {
        val r = repo()
        val d = r.ensureDefaultDriver().id
        val s = r.createSession(d, 1, 1_000L)
        r.saveLap(s, lap(1, 100_000)); r.saveLap(s, lap(2, 99_000, valid = false))
        r.endSession(s, 61_000L)
        r.createSession(d, 1, 2_000L)
        val rows = r.sessions().first().sortedBy { it.startedAtMs }
        assertEquals(listOf(2, 0), rows.map { it.lapCount })
        assertEquals(listOf(61_000L, null), rows.map { it.endedAtMs })
    }

    @Test fun carNamesSetOverwriteAndReset() {
        val r = repo()
        r.setCarName(3332, "Supra")
        r.setCarName(12, "Roadster")
        r.setCarName(3332, "Meine Supra")
        assertEquals(mapOf(3332 to "Meine Supra", 12 to "Roadster"), r.carNames())
        r.resetCarName(3332)
        assertEquals(mapOf(12 to "Roadster"), r.carNames())
    }

    @Test fun drivenCarsCountLapsNewestFirst() {
        val r = repo()
        val d = r.ensureDefaultDriver().id
        val s1 = r.createSession(d, 1, 1_000L)
        r.saveLap(s1, lap(1, 100_000)); r.saveLap(s1, lap(2, 99_000))
        r.createSession(d, 2, 5_000L)
        val s3 = r.createSession(d, 1, 3_000L)
        r.saveLap(s3, lap(1, 98_000))
        assertEquals(listOf(DrivenCar(2, 0, 5_000L), DrivenCar(1, 3, 3_000L)), r.drivenCars())
    }

    private fun Ring.lapOf(session: Long) = r.laps(session).single().id

    @Test fun comparisonLapPrefersSameCarThenSameDriverThenAnyone() {
        val r = repo(); val ring = Ring(r)
        val max = r.createDriver("Max"); val anna = r.createDriver("Anna")
        val mine = ring.lapOf(ring.drive(max, 1, 100_000))
        val annas = ring.lapOf(ring.drive(anna, 1, 80_000))
        ring.drive(max, 2, 90_000)
        val sameCar = ring.lapOf(ring.drive(max, 1, 99_000))
        assertEquals(ComparisonLap(sameCar, 99_000, "Max", ComparisonBasis.SAME_CAR), r.comparisonLap(ring.track, max, 1, mine))
        // without another lap in the same car: same driver in another car
        assertEquals(ComparisonBasis.SAME_DRIVER, r.comparisonLap(ring.track, max, 3, mine)?.basis)
        assertEquals(90_000, r.comparisonLap(ring.track, max, 3, mine)?.timeMs)
        // a driver without other laps gets the fastest lap of anyone
        val tom = r.createDriver("Tom")
        val toms = ring.lapOf(ring.drive(tom, 1, 120_000))
        assertEquals(ComparisonLap(annas, 80_000, "Anna", ComparisonBasis.ANY), r.comparisonLap(ring.track, tom, 1, toms))
    }

    @Test fun comparisonLapSkipsTheLapItselfInvalidLapsAndOtherTracks() {
        val r = repo(); val ring = Ring(r)
        val max = r.createDriver("Max")
        val mine = ring.lapOf(ring.drive(max, 1, 100_000))
        ring.drive(max, 1, 90_000, valid = false)
        val other = r.createTrack("Monza", floatArrayOf(0f))
        val s = r.createSession(max, 1, 99L)
        r.assignTrack(s, other)
        r.saveLap(s, lap(1, 70_000))
        assertNull(r.comparisonLap(ring.track, max, 1, mine))
    }

    @Test fun renameSessionStoresNameAndBlankClearsIt() = runBlocking {
        val r = repo()
        val s = r.createSession(r.ensureDefaultDriver().id, 1, 0L)
        r.renameSession(s, "  Quali-Training ")
        assertEquals("Quali-Training", r.sessions().first().single().name)
        r.renameSession(s, "   ")
        assertNull(r.sessions().first().single().name)
    }

    @Test fun deleteSessionRemovesItsLapsAndSamplesOnly() = runBlocking {
        val r = repo()
        val d = r.ensureDefaultDriver().id
        val gone = r.createSession(d, 1, 0L)
        val goneLap = r.saveLap(gone, lap(1, 100_000))
        val kept = r.createSession(d, 1, 1L)
        val keptLap = r.saveLap(kept, lap(1, 99_000))
        r.deleteSession(gone)
        assertEquals(listOf(kept), r.sessions().first().map { it.id })
        assertEquals(emptyList(), r.samples(goneLap))
        assertEquals(3, r.samples(keptLap).size)
    }

    @Test fun referenceLapIsUniquePerDriverAndTrack() {
        val r = repo(); val ring = Ring(r)
        val max = r.createDriver("Max"); val anna = r.createDriver("Anna")
        val first = ring.lapOf(ring.drive(max, 1, 100_000))
        val second = ring.lapOf(ring.drive(max, 2, 101_000))
        val annas = ring.lapOf(ring.drive(anna, 1, 99_000))
        val other = r.createTrack("Monza", floatArrayOf(0f))
        val s = r.createSession(max, 1, 99L); r.assignTrack(s, other)
        val monza = r.saveLap(s, lap(1, 80_000))
        listOf(first, annas, monza).forEach { r.setReferenceLap(it, true) }
        r.setReferenceLap(second, true)
        assertEquals(listOf(false, true, true, true), listOf(first, second, annas, monza).map { isRef(r, it) })
        r.setReferenceLap(second, false)
        assertEquals(false, isRef(r, second))
    }

    private fun isRef(r: SessionRepository, lapId: Long): Boolean =
        runBlocking { r.sessions().first() }.flatMap { r.laps(it.id) }.single { it.id == lapId }.isReference

    @Test fun bestLapPrefersTheReferenceLapEvenInAnotherCar() {
        val r = repo(); val ring = Ring(r)
        val anna = r.createDriver("Anna")
        ring.drive(anna, 1, 95_000)
        val ref = ring.lapOf(ring.drive(anna, 2, 99_000))
        r.setReferenceLap(ref, true)
        val best = r.bestLap(ring.track, anna, 1)
        assertEquals(99_000, best?.timeMs)
        assertEquals(2, best?.carCode)
    }

    @Test fun comparisonLapPrefersTheReferenceLapButNotTheLapItself() {
        val r = repo(); val ring = Ring(r)
        val max = r.createDriver("Max")
        val mine = ring.lapOf(ring.drive(max, 1, 100_000))
        ring.drive(max, 1, 95_000)
        val ref = ring.lapOf(ring.drive(max, 2, 99_000))
        r.setReferenceLap(ref, true)
        assertEquals(ComparisonLap(ref, 99_000, "Max", ComparisonBasis.REFERENCE), r.comparisonLap(ring.track, max, 1, mine))
        // the reference lap itself compares with the next best choice
        assertEquals(ComparisonBasis.SAME_DRIVER, r.comparisonLap(ring.track, max, 2, ref)?.basis)
    }
}
