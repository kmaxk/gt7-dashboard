package de.stiller.gt7.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.stiller.gt7.db.Gt7Database
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Properties
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals

/** Schema as shipped in 0.0.1 (user_version 1), before drivers existed. */
internal val V1_SCHEMA = listOf(
    "CREATE TABLE setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
    "CREATE TABLE track (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, outline BLOB NOT NULL)",
    "CREATE TABLE session (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, track_id INTEGER REFERENCES track(id), car_code INTEGER NOT NULL, started_at_ms INTEGER NOT NULL, ended_at_ms INTEGER)",
    "CREATE TABLE lap (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES session(id), number INTEGER NOT NULL, time_ms INTEGER NOT NULL, measured_ms INTEGER NOT NULL, fuel_used REAL NOT NULL, is_valid INTEGER NOT NULL)",
    "CREATE TABLE lap_sample (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, lap_id INTEGER NOT NULL REFERENCES lap(id), time_ms INTEGER NOT NULL, distance_m REAL NOT NULL, speed_mps REAL NOT NULL, throttle REAL NOT NULL, brake REAL NOT NULL, gear INTEGER NOT NULL, rpm REAL NOT NULL, x REAL NOT NULL, z REAL NOT NULL, boost_bar REAL NOT NULL)",
    "CREATE INDEX lap_sample_lap_id ON lap_sample(lap_id)",
    "CREATE INDEX lap_session_id ON lap(session_id)",
)

class MigrationTest {
    @Test fun migratesV1DatabaseKeepingSessions() = runBlocking {
        val file = createTempFile("gt7-v1", ".db")
        try {
            val url = "jdbc:sqlite:$file"
            JdbcSqliteDriver(url).use { old ->
                V1_SCHEMA.forEach { old.execute(null, it, 0) }
                old.execute(null, "INSERT INTO session(car_code, started_at_ms) VALUES (1, 1000)", 0)
                old.execute(null, "INSERT INTO session(car_code, started_at_ms) VALUES (2, 2000)", 0)
                old.execute(null, "INSERT INTO lap(session_id, number, time_ms, measured_ms, fuel_used, is_valid) VALUES (1, 1, 100000, 100000, 4.5, 1)", 0)
                old.execute(null, "PRAGMA user_version = 1", 0)
            }
            JdbcSqliteDriver(url, Properties(), Gt7Database.Schema).use { migrated ->
                val repo = SessionRepository(Gt7Database(migrated))
                assertEquals(listOf(Driver(1, "Fahrer 1")), repo.drivers())
                val sessions = repo.sessions().first()
                assertEquals(listOf(1L, 1L), sessions.map { it.driverId })
                assertEquals(1, repo.laps(1).size)
                val version = migrated.executeQuery(null, "PRAGMA user_version", { c -> c.next(); app.cash.sqldelight.db.QueryResult.Value(c.getLong(0)) }, 0).value
                assertEquals(5L, version)
                // migration 2: tracks gain an optional line lap
                val t = repo.createTrack("Ring", floatArrayOf(0f))
                assertEquals(listOf(null), repo.trackSummaries().map { it.lineLapId })
                repo.setTrackLine(t, 1L)
                assertEquals(listOf(1L), repo.trackSummaries().map { it.lineLapId })
                // migration 3: own car names
                assertEquals(emptyMap(), repo.carNames())
                repo.setCarName(1, "Eigenbau")
                assertEquals(mapOf(1 to "Eigenbau"), repo.carNames())
                // migration 4: session names and reference laps
                assertEquals(listOf(null, null), sessions.map { it.name })
                assertEquals(listOf(false), repo.laps(1).map { it.isReference })
                repo.renameSession(1, "Alt")
                repo.setReferenceLap(1, true)
                assertEquals("Alt", repo.sessions().first().first { it.id == 1L }.name)
                assertEquals(listOf(true), repo.laps(1).map { it.isReference })
            }
        } finally {
            file.deleteIfExists()
        }
    }
}
