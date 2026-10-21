package de.stiller.gt7.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.stiller.gt7.db.Gt7Database
import de.stiller.gt7.session.CompletedLap
import de.stiller.gt7.session.LapSample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataTransferTest {
    private val dir = createTempDirectory("gt7-transfer").toFile()
    private val drivers = mutableListOf<SqlDriver>()

    @AfterTest fun cleanUp() {
        drivers.forEach { it.close() }
        dir.deleteRecursively()
    }

    private inner class Db(name: String) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(dir, "$name.db")}", Properties(), Gt7Database.Schema).also { drivers += it }
        val repo = SessionRepository(Gt7Database(driver))
        val transfer = DataTransfer(Gt7Database(driver), dir.absolutePath)
        fun sessions() = runBlocking { repo.sessions().first() }
    }

    private fun sample(t: Int) = LapSample(t, t * 0.5f, 30f + t, 0.5f, 0.25f, 3, 6000f, t * 1.5f, -t * 2f, 0.75f)

    private fun lap(n: Int, timeMs: Int, valid: Boolean = true) =
        CompletedLap(n, timeMs, timeMs + 7, 4.5f, valid, listOf(sample(n), sample(n + 100)))

    /** Max drove two laps on Ring (line = the slower one), Anna one lap without track. */
    private fun filled(): Db = Db("a").apply {
        val max = repo.createDriver("Max")
        val anna = repo.createDriver("Anna")
        val ring = repo.createTrack("Ring", floatArrayOf(1f, 2f, 3f))
        val s1 = repo.createSession(max, 3332, 1_000L)
        repo.assignTrack(s1, ring)
        repo.saveLap(s1, lap(1, 100_000))
        val slow = repo.saveLap(s1, lap(2, 101_000))
        repo.endSession(s1, 9_000L)
        repo.setTrackLine(ring, slow)
        repo.saveLap(repo.createSession(anna, 12, 2_000L), lap(1, 90_000, valid = false))
        repo.setCarName(3332, "Supra")
        repo.setSetting("ps5Host", "192.168.1.5")
    }

    private fun export(db: Db): String = File(dir, "export.gt7export").absolutePath.also { db.transfer.exportTo(it) }

    @Test fun exportThenImportCopiesEverythingButSettings() {
        val a = filled()
        val file = export(a)
        val b = Db("b")
        assertEquals(ImportResult(drivers = 2, tracks = 1, sessions = 2, skippedSessions = 0, carNames = 1), b.transfer.importFrom(file))

        assertEquals(listOf("Max", "Anna"), b.repo.drivers().map { it.name })
        val track = b.repo.tracks().single()
        assertEquals("Ring", track.name)
        assertContentEquals(floatArrayOf(1f, 2f, 3f), track.outline)
        val sessions = b.sessions().sortedBy { it.startedAtMs }
        assertEquals(listOf("Max" to "Ring", "Anna" to null), sessions.map { it.driverName to it.trackName })
        val laps = b.repo.laps(sessions[0].id)
        assertEquals(listOf(100_000, 101_000), laps.map { it.timeMs })
        assertEquals(lap(2, 0).samples, b.repo.samples(laps[1].id))
        assertEquals(laps[1].id, b.repo.trackSummaries().single().lineLapId)
        assertEquals(listOf(false), b.repo.laps(sessions[1].id).map { it.isValid })
        assertEquals(mapOf(3332 to "Supra"), b.repo.carNames())
        assertNull(b.repo.setting("ps5Host"))
        // the export file itself carries no settings, the source keeps them
        assertNull(SessionRepository(Gt7Database(openDatabaseFile(file).also { drivers += it })).setting("ps5Host"))
        assertEquals("192.168.1.5", a.repo.setting("ps5Host"))
    }

    @Test fun exportHasCurrentSchemaVersion() {
        val file = export(filled())
        assertEquals(Gt7Database.Schema.version.toInt(), sqliteUserVersion(File(file).readBytes().copyOf(100)))
    }

    /** Two different tracks that share a name: GP and Nordschleife. */
    private fun twoNuerburgrings(): Db = Db("a").apply {
        val d = repo.createDriver("Max")
        val gp = repo.createTrack("Nürburgring", floatArrayOf(1f))
        val nos = repo.createTrack("Nürburgring", floatArrayOf(2f))
        repo.assignTrack(repo.createSession(d, 1, 1L), gp)
        repo.assignTrack(repo.createSession(d, 1, 2L), nos)
    }

    @Test fun sameNamedTracksStaySeparate() {
        val file = export(twoNuerburgrings())
        val b = Db("b")
        assertEquals(2, b.transfer.importFrom(file).tracks)
        assertEquals(listOf(1f, 2f), b.repo.tracks().map { it.outline.single() })
        assertEquals(2, b.sessions().map { it.trackId }.distinct().size)
    }

    @Test fun sameNamedTracksJoinAnExistingLocalTrack() {
        val file = export(twoNuerburgrings())
        val b = Db("b")
        val local = b.repo.createTrack("nürburgring", floatArrayOf(9f))
        assertEquals(0, b.transfer.importFrom(file).tracks)
        assertEquals(listOf(local, local), b.sessions().map { it.trackId })
    }

    /** GP and Nordschleife share a name but not their shape. */
    private fun Db.gpAndNordschleife(): Pair<Long, Long> {
        val d = repo.createDriver("Max")
        val gp = repo.createTrack("Nürburgring", floatArrayOf(0f, 0f))
        val nos = repo.createTrack("Nürburgring", floatArrayOf(500f, 500f))
        repo.assignTrack(repo.createSession(d, 1, 1L), gp)
        repo.assignTrack(repo.createSession(d, 1, 2L), nos)
        return gp to nos
    }

    @Test fun roundTripKeepsSameNamedTracksApart() {
        val a = Db("a")
        val (_, nos) = a.gpAndNordschleife()
        val b = Db("b")
        b.transfer.importFrom(export(a))
        // on b a new Nordschleife session is driven and sent back
        val bNos = b.repo.tracks().single { it.outline.contentEquals(floatArrayOf(500f, 500f)) }.id
        b.repo.assignTrack(b.repo.createSession(b.repo.drivers().single().id, 1, 3L), bNos)
        val back = File(dir, "back.gt7export").absolutePath.also { b.transfer.exportTo(it) }

        assertEquals(0, a.transfer.importFrom(back).tracks)
        assertEquals(nos, a.sessions().single { it.startedAtMs == 3L }.trackId)
    }

    @Test fun sameNamedTracksPickTheNearestOutline() {
        val a = Db("a")
        val (_, nos) = a.gpAndNordschleife()
        val other = Db("other")
        val t = other.repo.createTrack("NÜRBURGRING", floatArrayOf(490f, 505f))
        other.repo.assignTrack(other.repo.createSession(other.repo.createDriver("Anna"), 1, 9L), t)

        a.transfer.importFrom(export(other))
        assertEquals(nos, a.sessions().single { it.startedAtMs == 9L }.trackId)
    }

    @Test fun exportOverwritesAnExistingFile() {
        val a = filled()
        val target = File(dir, "export.gt7export").apply { writeText("old") }
        a.transfer.exportTo(target.absolutePath)
        assertEquals(2, Db("b").transfer.importFrom(target.absolutePath).sessions)
    }

    @Test fun importingTwiceSkipsKnownSessions() {
        val file = export(filled())
        val b = Db("b")
        b.transfer.importFrom(file)
        assertEquals(ImportResult(drivers = 0, tracks = 0, sessions = 0, skippedSessions = 2, carNames = 0), b.transfer.importFrom(file))
        assertEquals(2, b.sessions().size)
        assertEquals(1, b.repo.tracks().size)
    }

    @Test fun mergesDriversAndTracksByNameKeepingLocalChoices() {
        val file = export(filled())
        val b = Db("b")
        val max = b.repo.createDriver("max")
        val ring = b.repo.createTrack("RING", floatArrayOf(9f))
        b.repo.setCarName(3332, "Meine Supra")
        b.repo.setCarName(1, "Eigenbau")

        assertEquals(ImportResult(drivers = 1, tracks = 0, sessions = 2, skippedSessions = 0, carNames = 0), b.transfer.importFrom(file))
        assertEquals(listOf("max", "Anna"), b.repo.drivers().map { it.name })
        val maxSession = b.sessions().single { it.startedAtMs == 1_000L }
        assertEquals(max, maxSession.driverId)
        assertEquals(ring, maxSession.trackId)
        assertContentEquals(floatArrayOf(9f), b.repo.tracks().single().outline)
        assertNull(b.repo.trackSummaries().single().lineLapId)
        assertEquals(mapOf(3332 to "Meine Supra", 1 to "Eigenbau"), b.repo.carNames())
    }

    @Test fun importsOldSchemaWithoutChangingTheFile() {
        val old = File(dir, "old.gt7export")
        JdbcSqliteDriver("jdbc:sqlite:$old").use { d ->
            V1_SCHEMA.forEach { d.execute(null, it, 0) }
            d.execute(null, "INSERT INTO session(car_code, started_at_ms) VALUES (7, 1000)", 0)
            d.execute(null, "INSERT INTO lap(session_id, number, time_ms, measured_ms, fuel_used, is_valid) VALUES (1, 1, 100000, 100000, 4.5, 1)", 0)
            d.execute(null, "PRAGMA user_version = 1", 0)
        }
        val before = old.readBytes()
        val b = Db("b")
        b.repo.ensureDefaultDriver()
        assertEquals(ImportResult(drivers = 0, tracks = 0, sessions = 1, skippedSessions = 0, carNames = 0), b.transfer.importFrom(old.absolutePath))
        assertEquals(listOf("Fahrer 1"), b.sessions().map { it.driverName })
        assertContentEquals(before, old.readBytes())
    }

    @Test fun unreadableFilesFailWithoutChanges() {
        val b = Db("b")
        b.repo.createDriver("Max")
        val empty = File(dir, "empty.gt7export").apply { writeBytes(ByteArray(0)) }
        val garbage = File(dir, "garbage.gt7export").apply { writeText("das ist keine Datenbank, sondern nur Text ".repeat(50)) }
        val newer = File(dir, "newer.gt7export")
        JdbcSqliteDriver("jdbc:sqlite:$newer").use { it.execute(null, "PRAGMA user_version = 99", 0) }

        assertFailsWith<ImportException> { b.transfer.importFrom(empty.absolutePath) }
        assertFailsWith<ImportException> { b.transfer.importFrom(garbage.absolutePath) }
        val e = assertFailsWith<ImportException> { b.transfer.importFrom(newer.absolutePath) }
        assertTrue("neueren" in e.message.orEmpty(), e.message)
        assertFailsWith<ImportException> { b.transfer.importFrom(File(dir, "missing.gt7export").absolutePath) }

        assertEquals(listOf("Max"), b.repo.drivers().map { it.name })
        // no temporary copies stay behind
        assertEquals(setOf("b.db", "empty.gt7export", "garbage.gt7export", "newer.gt7export"), dir.list()!!.filterNot { it.endsWith("-journal") }.toSet())
    }

    @Test fun failedImportRollsBack() {
        val file = export(filled())
        val b = Db("b")
        // a trigger makes inserting the second lap fail midway
        b.driver.execute(null, "CREATE TRIGGER no_second BEFORE INSERT ON lap WHEN NEW.number = 2 BEGIN SELECT RAISE(ABORT, 'nope'); END", 0)
        assertFailsWith<Exception> { b.transfer.importFrom(file) }
        assertEquals(emptyList(), b.repo.drivers())
        assertEquals(0, b.sessions().size)
        assertEquals(emptyMap(), b.repo.carNames())
    }

    @Test fun importCarriesSessionNameAndReferenceUnlessOneExistsLocally() {
        val a = filled()
        val s = a.sessions().first { it.driverName == "Max" }
        a.repo.renameSession(s.id, "Quali")
        val fast = a.repo.laps(s.id).first { it.number == 1 }
        a.repo.setReferenceLap(fast.id, true)
        val file = export(a)

        val fresh = Db("b")
        fresh.transfer.importFrom(file)
        val imported = fresh.sessions().first { it.driverName == "Max" }
        assertEquals("Quali", imported.name)
        assertEquals(listOf(true, false), fresh.repo.laps(imported.id).map { it.isReference })

        // Max already has a reference lap on Ring here: the imported one stays unmarked
        val local = Db("c")
        val max = local.repo.createDriver("Max")
        val ring = local.repo.createTrack("Ring", floatArrayOf(1f, 2f, 3f))
        val own = local.repo.createSession(max, 1, 50_000L)
        local.repo.assignTrack(own, ring)
        val ownLap = local.repo.saveLap(own, lap(1, 120_000))
        local.repo.setReferenceLap(ownLap, true)
        local.transfer.importFrom(file)
        val refs = local.sessions().flatMap { local.repo.laps(it.id) }.filter { it.isReference }.map { it.id }
        assertEquals(listOf(ownLap), refs)
    }
}
