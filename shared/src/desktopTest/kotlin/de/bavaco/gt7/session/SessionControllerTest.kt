package de.bavaco.gt7.session

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.bavaco.gt7.db.Gt7Database
import de.bavaco.gt7.storage.ImportResult
import de.bavaco.gt7.storage.SessionRepository
import de.bavaco.gt7.telemetry.RawPacket
import de.bavaco.gt7.telemetry.TelemetrySource
import de.bavaco.gt7.telemetry.encryptForTest
import de.bavaco.gt7.telemetry.plainPacket
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FRAME_MS = 100L
private const val RADIUS_M = 100f
private const val PRE_FRAMES = 5

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private fun repo(): SessionRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Gt7Database.Schema.create(driver)
        return SessionRepository(Gt7Database(driver))
    }

    /** Emits [packets] one every FRAME_MS, starting immediately, stamped with the virtual clock. */
    private class FakeSource(private val bytes: List<ByteArray>, private val nowMs: () -> Long) : TelemetrySource {
        override val packets: Flow<RawPacket> = flow {
            bytes.forEachIndexed { i, p ->
                if (i > 0) delay(FRAME_MS)
                emit(RawPacket(nowMs(), p))
            }
        }
    }

    /**
     * Drives a circle of RADIUS_M at [speedMps]: PRE_FRAMES standing on lap 0, then [laps] full laps
     * (lap increments with lastLapMs = frames × FRAME_MS), then [extraFrames] into the next lap,
     * optionally followed by loading frames that end the session. Pick speeds whose consecutive laps
     * differ in frame count: the engine treats a repeated lastLapMs as "not yet updated" (lap invalid).
     */
    private fun circleLaps(speedMps: Float, laps: Int, extraFrames: Int, endWithLoading: Boolean = false): List<ByteArray> {
        val circumference = 2f * PI.toFloat() * RADIUS_M
        val stepM = speedMps * FRAME_MS / 1000f
        val drivingFrames = (laps * circumference / stepM).toInt() + 1 + extraFrames
        val out = ArrayList<ByteArray>()
        var lap = 0
        var lastIncrement = 0
        var lastLapMs = 0
        for (k in 0 until PRE_FRAMES + drivingFrames) {
            val driving = k >= PRE_FRAMES
            val dist = if (driving) (k - PRE_FRAMES) * stepM else 0f
            val newLap = if (driving) 1 + floor(dist / circumference).toInt() else 0
            if (newLap != lap) {
                if (lap > 0) lastLapMs = ((k - lastIncrement) * FRAME_MS).toInt()
                lastIncrement = k
                lap = newLap
            }
            val angle = dist / RADIUS_M
            out += packet(
                id = k + 1, x = RADIUS_M * cos(angle), z = RADIUS_M * sin(angle),
                speed = if (driving) speedMps else 0f, lap = lap, lastLapMs = lastLapMs, loading = false,
            )
        }
        // 0.5 s of loading: a single loading frame is taken as a glitch
        if (endWithLoading) repeat(6) { out += packet(PRE_FRAMES + drivingFrames + 1 + it, 0f, 0f, 0f, lap, lastLapMs, loading = true) }
        return out
    }

    private fun packet(id: Int, x: Float, z: Float, speed: Float, lap: Int, lastLapMs: Int, loading: Boolean): ByteArray =
        encryptForTest(plainPacket {
            float(0x04, x); float(0x0C, z); float(0x4C, speed); float(0x44, 50f)
            int(0x70, id); short(0x74, lap); int(0x7C, lastLapMs); int(0x124, 1234)
            short(0x8E, if (loading) 0x04 else 0x01)
        }, id)

    private fun framesFor(laps: Int, speedMps: Float = 55f): Long {
        val stepM = speedMps * FRAME_MS / 1000f
        return PRE_FRAMES + (laps * 2f * PI.toFloat() * RADIUS_M / stepM).toLong() + 1
    }

    private fun TestScope.controller(repo: SessionRepository) =
        SessionController(backgroundScope, repo) { testScheduler.currentTime }

    @Test
    fun firstValidLapBecomesReference() = runTest {
        val c = controller(repo())
        c.startSource(FakeSource(circleLaps(55f, laps = 1, extraFrames = 30), { testScheduler.currentTime }))
        advanceTimeBy(framesFor(laps = 1) * FRAME_MS / 2)
        runCurrent()
        assertTrue(c.live.value.sessionActive)
        assertNull(c.live.value.deltaMs)
        advanceTimeBy(framesFor(laps = 1) * FRAME_MS / 2 + 30 * FRAME_MS)
        runCurrent()
        assertTrue(c.live.value.sessionActive)
        assertNotNull(c.live.value.deltaMs)
    }

    @Test
    fun unknownTrackIsCreatedWithoutAsking() = runTest {
        val r = repo()
        r.createTrack("Neue Strecke 1", FloatArray(2 * TrackMatcher.POINTS) { 1_000f }) // another track, far off the circle
        val c = controller(r)
        c.startSource(FakeSource(circleLaps(55f, laps = 1, extraFrames = 10), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1) + 10) * FRAME_MS)
        runCurrent()
        val notice = assertNotNull(c.trackNotice.value)
        assertEquals("Neue Strecke 2", notice.name) // a free placeholder name, renamed later in the tracks tab
        assertTrue(notice.isNew)
        assertEquals(notice.trackId, r.sessions().first().single().trackId)
        c.dismissTrackNotice(); runCurrent()
        assertNull(c.trackNotice.value)
    }

    @Test
    fun knownTrackIsAssignedWithoutAsking() = runTest {
        val r = repo()
        val c = controller(r)
        // session 1: fast laps (55 m/s) on a new track
        c.startSource(FakeSource(circleLaps(55f, laps = 2, extraFrames = 5, endWithLoading = true), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 2) + 10) * FRAME_MS)
        runCurrent()
        val spa = r.tracks().single()

        // session 2: same circle, slower
        c.startSource(FakeSource(circleLaps(40f, laps = 2, extraFrames = 40), { testScheduler.currentTime }))
        val lap1Frames = framesFor(laps = 1, speedMps = 40f)
        advanceTimeBy((lap1Frames + 5) * FRAME_MS)
        runCurrent()
        assertEquals(TrackNotice(spa.id, spa.name, isNew = false), c.trackNotice.value)
        // halfway through lap 2: reference is now session 1's best (55 m/s) → we are clearly slower
        advanceTimeBy(lap1Frames / 2 * FRAME_MS)
        runCurrent()
        val delta = c.live.value.deltaMs!!
        assertTrue(delta > 1_000, "delta $delta") // ≈ 330 m × (1/40 − 1/55) s/m ≈ 2.3 s
        assertEquals(spa.id, r.sessions().first().first().trackId)
    }

    @Test
    fun lapsArePersisted() = runTest {
        val r = repo()
        val c = controller(r)
        c.startSource(FakeSource(circleLaps(55f, laps = 2, extraFrames = 5, endWithLoading = true), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 2) + 10) * FRAME_MS)
        runCurrent()
        val session = r.sessions().first().single()
        val laps = r.laps(session.id)
        assertEquals(listOf(1, 2), laps.map { it.number })
        assertTrue(laps.all { it.isValid }, "laps $laps")
        assertTrue(laps.all { it.timeMs in 11_400..11_500 }, "times ${laps.map { it.timeMs }}")
        assertTrue(r.samples(laps[0].id).size > 100, "samples ${r.samples(laps[0].id).size}")
    }

    @Test
    fun connectionTurnsWaitingAfter2s() = runTest {
        val c = controller(repo())
        assertEquals(ConnectionStatus.Idle, c.connection.value)
        c.startSource(FakeSource(circleLaps(55f, laps = 0, extraFrames = 5), { testScheduler.currentTime }))
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(ConnectionStatus.Receiving, c.connection.value)
        val lastPacketAt = c.stats.value.lastPacketAtMs!!
        assertEquals(1_000L, lastPacketAt) // 11 packets, 100 ms apart
        advanceTimeBy(1_000) // t = 2500, tick at 2000: packet 1000 ms old
        runCurrent()
        assertEquals(ConnectionStatus.Receiving, c.connection.value)
        advanceTimeBy(2_000) // t = 4500, tick at 4000: packet 3000 ms old
        runCurrent()
        assertEquals(ConnectionStatus.Waiting, c.connection.value)
    }

    @Test
    fun rejectedTrackIsReplacedByANewOne() = runTest {
        val r = repo()
        val spa = r.createTrack("Spa", TrackMatcher.outlineOf(oneLapSamples()))
        val c = controller(r)
        c.startSource(FakeSource(circleLaps(55f, laps = 1, extraFrames = 10), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1) + 10) * FRAME_MS)
        runCurrent()
        assertEquals(spa, c.trackNotice.value?.trackId)
        c.rejectTrack()
        runCurrent()
        val created = r.tracks().single { it.id == r.sessions().first().single().trackId }
        assertEquals("Neue Strecke 1", created.name)
        assertEquals(TrackNotice(created.id, created.name, isNew = true), c.trackNotice.value)
        assertEquals(2, r.tracks().size)
    }

    @Test
    fun sourceFailureSetsIdleAndKeepsControllerAlive() = runTest {
        val c = controller(repo())
        val failing = object : TelemetrySource {
            override val packets: Flow<RawPacket> = flow { throw IllegalStateException("port 33740 in use") }
        }
        c.startSource(failing)
        runCurrent()
        assertEquals(ConnectionStatus.Idle, c.connection.value)
        advanceTimeBy(1_500) // ticker keeps running and must not resurrect Waiting
        runCurrent()
        assertEquals(ConnectionStatus.Idle, c.connection.value)

        c.startSource(FakeSource(circleLaps(55f, laps = 0, extraFrames = 5), { testScheduler.currentTime }))
        advanceTimeBy(1_500)
        runCurrent()
        assertNotNull(c.live.value.frame)
        assertEquals(ConnectionStatus.Receiving, c.connection.value)
        advanceTimeBy(3_000) // ticker still alive
        runCurrent()
        assertEquals(ConnectionStatus.Waiting, c.connection.value)
    }

    /** Samples of one lap on the test circle, as the engine records them (10 Hz, distance from lap start). */
    private fun oneLapSamples(speedMps: Float = 55f): List<LapSample> {
        val circumference = 2f * PI.toFloat() * RADIUS_M
        val stepM = speedMps * FRAME_MS / 1000f
        return (0..(circumference / stepM).toInt()).map { k ->
            val d = k * stepM
            LapSample((k * FRAME_MS).toInt(), d, speedMps, 0f, 0f, 0, 0f, RADIUS_M * cos(d / RADIUS_M), RADIUS_M * sin(d / RADIUS_M), 0f)
        }
    }

    @Test
    fun importReloadsDriversAndTracks() = runTest {
        val r = repo()
        val c = controller(r)
        runCurrent()
        val version = c.tracksVersion.value
        val result = ImportResult(drivers = 1, tracks = 1, sessions = 0, skippedSessions = 0, carNames = 0)
        assertEquals(result, c.importData { r.createDriver("Anna"); r.createTrack("Spa", floatArrayOf(0f)); result })
        assertEquals(listOf("Fahrer 1", "Anna"), c.drivers.value.map { it.name })
        assertEquals(version + 1, c.tracksVersion.value)
    }

    @Test
    fun exclusiveReturnsBlockResult() = runTest {
        assertEquals(42, controller(repo()).exclusive { 42 })
    }

    @Test
    fun recordingStateReflectsStartStop() = runTest {
        val c = controller(repo())
        assertNull(c.recording.value)
        c.startRecording(kotlinx.io.Buffer(), "a.gt7rec")
        assertEquals("a.gt7rec", c.recording.value)
        c.stopRecording()
        assertNull(c.recording.value)
    }

    @Test
    fun startReplayStopsRecording() = runTest {
        val c = controller(repo())
        c.startRecording(kotlinx.io.Buffer(), "a.gt7rec")
        c.startReplay(emptyList())
        runCurrent()
        assertNull(c.recording.value)
    }

    /** Runs one session at [speedMps] for [laps] laps, ending it with a loading frame. */
    private fun TestScope.driveSession(c: SessionController, speedMps: Float, laps: Int) {
        c.startSource(FakeSource(circleLaps(speedMps, laps, extraFrames = 5, endWithLoading = true), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps, speedMps) + 10) * FRAME_MS)
        runCurrent()
    }

    /** Anna drives two laps at 55 m/s on a new track; afterwards "Fahrer 1" is active again. */
    private fun TestScope.annaSetsSpa(c: SessionController, r: SessionRepository): de.bavaco.gt7.storage.Driver {
        runCurrent()
        c.createDriver("Anna"); runCurrent()
        val anna = c.drivers.value.single { it.name == "Anna" }
        c.selectDriver(anna.id); runCurrent()
        driveSession(c, 55f, laps = 2)
        c.selectDriver(r.ensureDefaultDriver().id); runCurrent()
        return anna
    }

    @Test
    fun loadsDriversAndFallsBackForUnknownSettings() = runTest {
        val r = repo()
        r.setSetting("activeDriver", "99")
        r.setSetting("opponent", "42")
        val c = controller(r)
        runCurrent()
        assertEquals("Fahrer 1", c.activeDriver.value?.name)
        assertEquals(Opponent.Self, c.opponent.value)
    }

    @Test
    fun driverSwitchEndsSessionAndKeepsTrack() = runTest {
        val r = repo()
        val c = controller(r)
        runCurrent()
        c.startSource(FakeSource(circleLaps(55f, laps = 4, extraFrames = 5), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1) + 10) * FRAME_MS)
        runCurrent()
        val spa = r.tracks().single()
        c.createDriver("Anna"); runCurrent()
        c.selectDriver(c.drivers.value.single { it.name == "Anna" }.id)
        advanceTimeBy(20 * FRAME_MS)
        runCurrent()
        val sessions = r.sessions().first()
        assertEquals(2, sessions.size)
        assertEquals("Anna", sessions.first().driverName)
        assertEquals(spa.id, sessions.first().trackId)
        assertNull(c.trackNotice.value)
    }

    @Test
    fun opponentReferenceIsTheirBestLap() = runTest {
        val r = repo()
        val c = controller(r)
        val anna = annaSetsSpa(c, r)
        c.selectOpponent(Opponent.Of(anna)); runCurrent()
        c.startSource(FakeSource(circleLaps(40f, laps = 2, extraFrames = 40), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1, speedMps = 40f) + 5) * FRAME_MS)
        runCurrent()
        assertNotNull(c.trackNotice.value)
        val ref = assertNotNull(c.reference.value)
        assertEquals("Anna", ref.owner.name)
        assertEquals(false, ref.isSelf)
        assertTrue(ref.timeMs in 11_400..11_500, "time ${ref.timeMs}")
        assertTrue(ref.sameCar)
    }

    @Test
    fun ownFasterLapReplacesOnlySelfReference() = runTest {
        val r = repo()
        val c = controller(r)
        val anna = annaSetsSpa(c, r)
        c.selectOpponent(Opponent.Of(anna)); runCurrent()
        // Fahrer 1 is faster than Anna (60 vs 55 m/s)
        c.startSource(FakeSource(circleLaps(60f, laps = 3, extraFrames = 40), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1, speedMps = 60f) + 5) * FRAME_MS)
        runCurrent()
        advanceTimeBy(framesFor(laps = 2, speedMps = 60f) * FRAME_MS)
        runCurrent()
        assertEquals("Anna", c.reference.value?.owner?.name)
        c.selectOpponent(Opponent.Self); runCurrent()
        val own = assertNotNull(c.reference.value)
        assertEquals("Fahrer 1", own.owner.name)
        assertTrue(own.isSelf)
        assertTrue(own.timeMs < 11_000, "time ${own.timeMs}")
    }

    @Test
    fun opponentWithoutLapsGivesNoReference() = runTest {
        val r = repo()
        val c = controller(r)
        runCurrent()
        c.createDriver("Tom"); runCurrent()
        c.selectOpponent(Opponent.Of(c.drivers.value.single { it.name == "Tom" })); runCurrent()
        c.startSource(FakeSource(circleLaps(55f, laps = 2, extraFrames = 40), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1) + 5) * FRAME_MS)
        runCurrent()
        advanceTimeBy(framesFor(laps = 1) * FRAME_MS)
        runCurrent()
        assertNull(c.reference.value)
        assertNull(c.live.value.deltaMs)
    }

    @Test
    fun selectingOpponentAsDriverResetsToSelf() = runTest {
        val c = controller(repo())
        runCurrent()
        c.createDriver("Anna"); runCurrent()
        val anna = c.drivers.value.single { it.name == "Anna" }
        c.selectOpponent(Opponent.Of(anna)); runCurrent()
        c.selectDriver(anna.id); runCurrent()
        assertEquals(Opponent.Self, c.opponent.value)
    }

    @Test
    fun carriedTrackIsDroppedWhenGameLoads() = runTest {
        val r = repo()
        val c = controller(r)
        runCurrent()
        c.startSource(FakeSource(circleLaps(55f, laps = 1, extraFrames = 10), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1) + 10) * FRAME_MS)
        runCurrent()
        // still in the session (no timeout yet) when Anna takes over; then GT7 loads another track
        c.createDriver("Anna"); runCurrent()
        c.selectDriver(c.drivers.value.single { it.name == "Anna" }.id); runCurrent()
        val loading = (1..3).map { packet(it, 0f, 0f, 0f, 0, 0, loading = true) }
        c.startSource(FakeSource(loading + circleLaps(55f, laps = 2, extraFrames = 10), { testScheduler.currentTime }))
        advanceTimeBy(20 * FRAME_MS)
        runCurrent()
        val annas = r.sessions().first().first()
        assertEquals("Anna", annas.driverName)
        assertNull(annas.trackId)
    }

    @Test
    fun nextDriverSeesTrackLineBeforeOwnLap() = runTest {
        val r = repo()
        val c = controller(r)
        runCurrent()
        c.startSource(FakeSource(circleLaps(55f, laps = 4, extraFrames = 5), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1) + 10) * FRAME_MS)
        runCurrent()
        assertNotNull(c.trackLine.value)
        c.createDriver("Anna"); runCurrent()
        c.selectDriver(c.drivers.value.single { it.name == "Anna" }.id)
        advanceTimeBy(20 * FRAME_MS)
        runCurrent()
        // Anna races herself and has no lap yet: no reference, but the map knows the track
        assertNull(c.reference.value)
        val line = assertNotNull(c.trackLine.value)
        assertTrue(line.size > 100, "samples ${line.size}")
    }

    @Test
    fun deletingOpponentRemovesLapsAndResetsToSelf() = runTest {
        val r = repo()
        val c = controller(r)
        val anna = annaSetsSpa(c, r)
        c.selectOpponent(Opponent.Of(anna)); runCurrent()
        c.deleteDriver(anna.id); runCurrent()
        assertEquals(listOf("Fahrer 1"), c.drivers.value.map { it.name })
        assertEquals(Opponent.Self, c.opponent.value)
        assertEquals("self", r.setting("opponent"))
        assertTrue(r.sessions().first().none { it.driverName == "Anna" })
    }

    @Test
    fun activeDriverCannotBeDeleted() = runTest {
        val r = repo()
        val c = controller(r)
        runCurrent()
        c.createDriver("Anna"); runCurrent()
        c.deleteDriver(c.activeDriver.value!!.id); runCurrent()
        assertEquals(2, c.drivers.value.size)
    }

    @Test
    fun knownTrackIsShownBeforeTheFirstLap() = runTest {
        val r = repo()
        val c = controller(r)
        annaSetsSpa(c, r)
        // Fahrer 1 starts a fresh session on the same circle: no carried track
        // 40 m/s: one path point every 8 m, so about 480 m (12 s) until the locator decides
        c.startSource(FakeSource(circleLaps(40f, laps = 2, extraFrames = 0), { testScheduler.currentTime }))
        advanceTimeBy(5 * 1000L)
        runCurrent()
        assertNull(c.trackLine.value) // too little distance yet
        advanceTimeBy(10 * 1000L)
        runCurrent()
        val line = assertNotNull(c.trackLine.value)
        assertTrue(line.size > 100)
        // check the live state first: sessions() delivers on a real thread, and while the test waits for it
        // runTest moves the virtual clock on, so the fake source would finish the lap and the question would come
        assertNull(c.trackNotice.value)
        assertNull(r.sessions().first().first().trackId) // only shown, not assigned before the lap
    }

    @Test
    fun deletingCurrentTrackClearsItAndKeepsLaps() = runTest {
        val r = repo()
        val c = controller(r)
        runCurrent()
        c.startSource(FakeSource(circleLaps(55f, laps = 3, extraFrames = 5), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1) + 10) * FRAME_MS)
        runCurrent()
        assertNotNull(c.trackLine.value)
        val v = c.tracksVersion.value
        c.deleteTrack(r.tracks().single().id); runCurrent()
        assertNull(c.trackLine.value)
        assertTrue(r.tracks().isEmpty())
        assertTrue(r.laps(r.sessions().first().single().id).isNotEmpty())
        assertTrue(c.tracksVersion.value > v)
    }

    @Test
    fun runningSessionCannotBeDeleted() = runTest {
        val r = repo()
        val c = controller(r)
        runCurrent()
        c.startSource(FakeSource(circleLaps(55f, laps = 2, extraFrames = 40), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1) + 5) * FRAME_MS)
        runCurrent()
        val running = assertNotNull(c.activeSession.value)
        assertFalse(c.deleteSession(running))
        assertEquals(listOf(running), r.sessions().first().map { it.id })
    }

    @Test
    fun endedSessionIsDeletedWithItsLaps() = runTest {
        val r = repo()
        val c = controller(r)
        runCurrent()
        driveSession(c, 55f, laps = 2)
        assertNull(c.activeSession.value)
        val id = r.sessions().first().single().id
        assertTrue(r.laps(id).isNotEmpty())
        assertTrue(c.deleteSession(id))
        assertTrue(r.sessions().first().isEmpty())
        assertTrue(r.laps(id).isEmpty())
    }

    @Test
    fun markedReferenceIsNotReplacedByAFasterLap() = runTest {
        val r = repo()
        val c = controller(r)
        annaSetsSpa(c, r)
        // Fahrer 1 sets a slow lap on Spa and marks it
        c.startSource(FakeSource(circleLaps(50f, laps = 3, extraFrames = 5, endWithLoading = true), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1, speedMps = 50f) + 5) * FRAME_MS)
        runCurrent()
        advanceTimeBy((framesFor(laps = 2, speedMps = 50f) + 10) * FRAME_MS)
        runCurrent()
        val slow = r.sessions().first().first { it.driverName == "Fahrer 1" }
        val marked = r.laps(slow.id).first { it.isValid }
        c.setReferenceLap(marked.id, true); runCurrent()
        assertEquals(marked.timeMs, c.reference.value?.timeMs)
        // a faster session keeps the marked lap as reference
        c.startSource(FakeSource(circleLaps(60f, laps = 3, extraFrames = 40), { testScheduler.currentTime }))
        advanceTimeBy((framesFor(laps = 1, speedMps = 60f) + 5) * FRAME_MS)
        runCurrent()
        advanceTimeBy(framesFor(laps = 2, speedMps = 60f) * FRAME_MS)
        runCurrent()
        assertEquals(marked.timeMs, c.reference.value?.timeMs)
    }
}
