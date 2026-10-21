package de.stiller.gt7.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.use
import de.stiller.gt7.db.Gt7Database
import de.stiller.gt7.session.CompletedLap
import de.stiller.gt7.session.LapSample
import de.stiller.gt7.storage.DataTransfer
import de.stiller.gt7.storage.Driver
import de.stiller.gt7.storage.SessionRepository
import de.stiller.gt7.storage.createDriver
import de.stiller.gt7.storage.openDatabaseFile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Schema as shipped in 0.0.1 (user_version 1), before drivers existed. */
private val V1_SCHEMA = listOf(
    "CREATE TABLE setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
    "CREATE TABLE track (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, outline BLOB NOT NULL)",
    "CREATE TABLE session (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, track_id INTEGER REFERENCES track(id), car_code INTEGER NOT NULL, started_at_ms INTEGER NOT NULL, ended_at_ms INTEGER)",
    "CREATE TABLE lap (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL REFERENCES session(id), number INTEGER NOT NULL, time_ms INTEGER NOT NULL, measured_ms INTEGER NOT NULL, fuel_used REAL NOT NULL, is_valid INTEGER NOT NULL)",
    "CREATE TABLE lap_sample (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, lap_id INTEGER NOT NULL REFERENCES lap(id), time_ms INTEGER NOT NULL, distance_m REAL NOT NULL, speed_mps REAL NOT NULL, throttle REAL NOT NULL, brake REAL NOT NULL, gear INTEGER NOT NULL, rpm REAL NOT NULL, x REAL NOT NULL, z REAL NOT NULL, boost_bar REAL NOT NULL)",
)

/** The Android SQLite driver with the app schema: storing laps, migrating, export and import. Gt7Application has run initPlatform. */
@RunWith(AndroidJUnit4::class)
class StorageSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun file(name: String) = File(context.cacheDir, name).apply { delete() }

    private val samples = listOf(
        LapSample(0, 0f, 30f, 1f, 0f, 3, 6000f, 1f, 2f, 0.5f),
        LapSample(100, 3f, 31f, 1f, 0f, 3, 6100f, 4f, 2f, 0.5f),
    )

    @Test fun appDatabaseOpens() {
        // read only: a connected real device keeps its own data untouched
        createDriver().use { driver -> SessionRepository(Gt7Database(driver)).drivers() }
    }

    @Test fun savesAndReadsALap() {
        openDatabaseFile(file("lap.db").absolutePath).use { driver ->
            val repo = SessionRepository(Gt7Database(driver))
            val session = repo.createSession(repo.ensureDefaultDriver().id, 3332, 1_000L)
            val lap = repo.saveLap(session, CompletedLap(1, 100_000, 100_000, 4.5f, true, samples))
            assertEquals(samples, repo.samples(lap))
        }
    }

    @Test fun migratesV1Database() {
        val v1 = file("v1.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(v1, null).use { db ->
            V1_SCHEMA.forEach(db::execSQL)
            db.execSQL("INSERT INTO session(car_code, started_at_ms) VALUES (1, 1000)")
            db.version = 1
        }
        openDatabaseFile(v1.absolutePath).use { driver ->
            assertEquals(listOf(Driver(1, "Fahrer 1")), SessionRepository(Gt7Database(driver)).drivers())
        }
    }

    @Test fun exportsAndImports() {
        val source = file("source.db")
        val target = file("target.db")
        val export = file("smoke.gt7export")
        openDatabaseFile(source.absolutePath).use { driver ->
            val db = Gt7Database(driver)
            val repo = SessionRepository(db)
            repo.saveLap(repo.createSession(repo.createDriver("Max"), 1, 1_000L), CompletedLap(1, 100_000, 100_000, 4.5f, true, samples))
            DataTransfer(db, context.cacheDir.absolutePath).exportTo(export.absolutePath)
        }
        openDatabaseFile(target.absolutePath).use { driver ->
            assertEquals(1, DataTransfer(Gt7Database(driver), context.cacheDir.absolutePath).importFrom(export.absolutePath).sessions)
        }
    }
}
