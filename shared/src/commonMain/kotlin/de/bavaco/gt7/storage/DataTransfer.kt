package de.bavaco.gt7.storage

import app.cash.sqldelight.db.use
import de.bavaco.gt7.db.Gt7Database
import de.bavaco.gt7.session.CompletedLap
import de.bavaco.gt7.session.Track
import de.bavaco.gt7.session.TrackMatcher
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlin.random.Random

const val EXPORT_EXTENSION = "gt7export"

/** What an import added; [skippedSessions] were already there. */
data class ImportResult(val drivers: Int, val tracks: Int, val sessions: Int, val skippedSessions: Int, val carNames: Int)

/** The file cannot be imported; the message is meant for the user. */
class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Export copies the whole database without settings into a fresh SQLite file; import merges such a file:
 * drivers and tracks by name, sessions unless known (same driver, car and start), own car names only where none exist.
 * [workDir] holds the temporary copy an import migrates, so the chosen file stays untouched.
 */
class DataTransfer(private val db: Gt7Database, private val workDir: String) {
    /** Returns once the file at [path] is written and closed. */
    fun exportTo(path: String) {
        val target = Path(path)
        if (SystemFileSystem.exists(target)) SystemFileSystem.delete(target)
        openDatabaseFile(path).use { merge(from = db, into = Gt7Database(it)) }
    }

    fun importFrom(path: String): ImportResult {
        val copy = Path(workDir, "import-${Random.nextLong().toULong()}.$EXPORT_EXTENSION")
        try {
            try {
                SystemFileSystem.source(Path(path)).buffered().use { src -> SystemFileSystem.sink(copy).buffered().use { src.transferTo(it) } }
            } catch (e: Exception) {
                throw ImportException("Die Datei konnte nicht gelesen werden.", e)
            }
            checkVersion(copy)
            return openDatabaseFile(copy.toString()).use { merge(from = Gt7Database(it), into = db) }
        } finally {
            if (SystemFileSystem.exists(copy)) SystemFileSystem.delete(copy)
        }
    }

    /** Rejects files that are no export or come from a newer app; older ones are migrated when opened. */
    private fun checkVersion(file: Path) {
        val header = SystemFileSystem.source(file).buffered().use { it.readByteArray(minOf(100L, SystemFileSystem.metadataOrNull(file)?.size ?: 0L).toInt()) }
        val version = sqliteUserVersion(header) ?: 0
        when {
            version == 0 -> throw ImportException("Die Datei ist keine GT7-Exportdatei.")
            version > Gt7Database.Schema.version -> throw ImportException("Die Datei wurde mit einer neueren App-Version exportiert. Bitte die App aktualisieren.")
        }
    }

    /** Of local tracks sharing a name (e.g. GP and Nordschleife), the one with the same or the nearest outline. */
    private fun sameName(outline: FloatArray, candidates: List<Track>): Long =
        candidates.singleOrNull()?.id
            ?: candidates.firstOrNull { it.outline.contentEquals(outline) }?.id
            ?: candidates.filter { it.outline.size == outline.size }.minByOrNull { TrackMatcher.meanDistance(outline, it.outline) }?.id
            ?: candidates.first().id

    private fun merge(from: Gt7Database, into: Gt7Database): ImportResult = into.transactionWithResult {
        val q = into.gt7Queries
        val src = from.gt7Queries
        val repo = SessionRepository(into)
        val fromRepo = SessionRepository(from)

        var newDrivers = 0
        val driverIds = fromRepo.drivers().associate { d ->
            d.name to (q.driverIdByName(d.name).executeAsOneOrNull() ?: repo.createDriver(d.name).also { newDrivers++ })
        }

        // only tracks that were here before match by name, so two imported tracks sharing a name stay apart;
        // local tracks keep outline and line, new ones get their line once the laps have new ids
        val localTracks = repo.tracks().groupBy { it.name.lowercase() }
        val newTracks = mutableMapOf<Long, Long?>()
        val trackIds = src.exportTracks().executeAsList().associate { t ->
            val outline = t.outline.toFloats()
            t.id to (localTracks[t.name.lowercase()]?.let { sameName(outline, it) }
                ?: repo.createTrack(t.name, outline).also { newTracks[it] = t.line_lap_id })
        }

        var sessions = 0
        var skipped = 0
        val lapIds = mutableMapOf<Long, Long>()
        for (s in src.exportSessions().executeAsList()) {
            val driverId = s.driver_name?.let { driverIds[it] }
            if (q.sessionExists(driverId, s.car_code, s.started_at_ms).executeAsOne() > 0) {
                skipped++
                continue
            }
            val trackId = s.track_id?.let { trackIds[it] }
            q.importSession(driverId, trackId, s.car_code, s.started_at_ms, s.ended_at_ms, s.name)
            val sessionId = q.lastInsertId().executeAsOne()
            for (l in src.exportLaps(s.id).executeAsList()) {
                val lap = CompletedLap(l.number.toInt(), l.time_ms.toInt(), l.measured_ms.toInt(), l.fuel_used.toFloat(), l.is_valid != 0L, fromRepo.samples(l.id))
                val id = repo.saveLap(sessionId, lap)
                lapIds[l.id] = id
                // a local reference lap of the driver on the track wins
                if (l.is_reference != 0L && (trackId == null || driverId == null || !repo.hasReferenceLap(trackId, driverId))) repo.setReferenceLap(id, true)
            }
            sessions++
        }
        for ((trackId, lineLap) in newTracks) lineLap?.let { lapIds[it] }?.let { repo.setTrackLine(trackId, it) }

        val ownNames = repo.carNames()
        val carNames = fromRepo.carNames().filterKeys { it !in ownNames }
        carNames.forEach { (code, name) -> repo.setCarName(code, name) }

        ImportResult(newDrivers, newTracks.size, sessions, skipped, carNames.size)
    }
}
