package de.stiller.gt7.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.stiller.gt7.db.Gt7Database
import de.stiller.gt7.session.Engineer
import de.stiller.gt7.session.LapSample
import de.stiller.gt7.session.SessionController
import de.stiller.gt7.storage.CarCatalog
import de.stiller.gt7.storage.SessionRepository
import de.stiller.gt7.telemetry.RawPacket
import de.stiller.gt7.telemetry.TelemetrySource
import de.stiller.gt7.telemetry.encryptForTest
import de.stiller.gt7.telemetry.plainPacket
import de.stiller.gt7.ui.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

/**
 * Renders the README screenshots from real data; runs only with SCREENSHOT_OUT and SCREENSHOT_DB set.
 * Works on its own temporary copy of SCREENSHOT_DB, so the app's database stays untouched.
 */
class ReadmeScreenshots {
    private val out = System.getenv("SCREENSHOT_OUT")?.let(::File)
    private val dbPath = System.getenv("SCREENSHOT_DB")

    private fun shot(name: String, w: Int, h: Int, frames: Int = 6, frameMs: Long = 150, start: () -> Unit = {}, content: @Composable () -> Unit) {
        ImageComposeScene(w, h, Density(1f)) {
            Gt7Theme { Surface(Modifier.fillMaxSize()) { content() } }
        }.use { scene ->
            var image = scene.render(0)
            start()
            // let effects load their data and live views collect frames
            repeat(frames) { i -> Thread.sleep(frameMs); image = scene.render((i + 1) * frameMs * 1_000_000L) }
            File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    /** One GT7 packet per 1/60 s along [lap] for [lapNumber], up to [untilMs] of it. */
    private fun packets(lap: List<LapSample>, lapNumber: Int, untilMs: Int, lastLapMs: Int, bestLapMs: Int, fuel: Float, startId: Int, startAt: Long): List<RawPacket> {
        val out = ArrayList<RawPacket>()
        var t = 0
        var id = startId
        while (t <= untilMs) {
            val s = sampleAt(lap, t / 1000f, AxisMode.TIME) ?: break
            val progress = t / lap.last().timeMs.toFloat()
            val plain = plainPacket {
                float(0x04, s.x); float(0x08, 0f); float(0x0C, s.z)
                float(0x3C, s.rpm); short(0x88, 15000); short(0x8A, 17500)
                float(0x44, fuel - progress * 2.2f); float(0x48, 100f)
                float(0x4C, s.speedMps); float(0x50, s.boostBar + 1f)
                float(0x54, 4.1f); float(0x58, 86f); float(0x5C, 97f)
                float(0x60, 78f + 6 * s.brake); float(0x64, 80f + 6 * s.brake); float(0x68, 74f); float(0x6C, 75f)
                int(0x70, id++)
                short(0x74, lapNumber); short(0x76, 0)
                int(0x78, bestLapMs); int(0x7C, lastLapMs)
                short(0x84, -1); short(0x86, -1)
                short(0x8E, 0x01 or 0x400 or 0x800)
                byte(0x90, s.gear or (15 shl 4))
                byte(0x91, (s.throttle * 255).toInt()); byte(0x92, (s.brake * 255).toInt())
                for (w in 0 until 4) { float(0xA4 + 4 * w, s.speedMps / 0.33f); float(0xB4 + 4 * w, 0.33f) }
                int(0x124, 3656)
            }
            out += RawPacket(startAt + t, encryptForTest(plain, id * 7919))
            t += 16
        }
        return out
    }

    @Test
    fun render() {
        if (out == null || dbPath == null) return
        out.mkdirs()
        val work = createTempDirectory("gt7-screenshots").toFile()
        try {
            render(copyDatabase(File(dbPath), work))
        } finally {
            work.deleteRecursively()
        }
    }

    /** The database with its write-ahead log, so recent laps are included. */
    private fun copyDatabase(source: File, dir: File): File {
        require(source.isFile) { "SCREENSHOT_DB is no file: $source" }
        for (suffix in listOf("", "-wal", "-shm")) {
            File(source.path + suffix).takeIf { it.isFile }?.copyTo(File(dir, "gt7.db$suffix"))
        }
        return File(dir, "gt7.db")
    }

    private fun render(db: File) {
        val repo = SessionRepository(Gt7Database(JdbcSqliteDriver("jdbc:sqlite:$db", Properties(), Gt7Database.Schema)))
        val res = File("src/commonMain/composeResources/files")
        val cars = CarCatalog(File(res, "cars.csv").readText(), File(res, "maker.csv").readText())
        // only the owner's name goes public; own session names may be private too
        repo.drivers().filter { it.name != "Max" }.forEachIndexed { i, d -> repo.renameDriver(d.id, "Fahrer ${i + 2}") }
        runBlocking { repo.sessions().first() }.forEach { repo.renameSession(it.id, null) }
        // before the live replay adds a session of its own
        val sessions = runBlocking { repo.sessions().first() }
        val byBest = SessionOverviewState().apply { sort = SessionSort(SessionColumn.Best, descending = false) }
        shot("sessions", 1600, 420) { SessionOverview(sessions, cars, byBest, null, {}, {}, Modifier.fillMaxSize()) }
        val fast = repo.samples(17)
        val slow = repo.samples(14)

        // live: an out-lap tail, the fast lap, then half of the slow lap
        // laps get today's date: the replay ends now
        var clock = System.currentTimeMillis() - 200_000L
        val feed = packets(slow, 1, slow.last().timeMs, 0, -1, 80f, 1, clock).takeLast(600).let { tail ->
            val shift = tail.first().atMs - clock
            tail.map { RawPacket(it.atMs - shift, it.bytes) }
        }.toMutableList()
        feed += packets(fast, 2, fast.last().timeMs, -1, -1, 79f, 100_000, feed.last().atMs + 16)
        feed += packets(slow, 3, slow.last().timeMs * 55 / 100, 85_785, 85_785, 77f, 200_000, feed.last().atMs + 16)
        // the last 10 s arrive in real time while the live screen is drawn, so trace and map trail fill up
        val tail = feed.takeLast(600)
        val head = feed.dropLast(600)
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = SessionController(scope, repo) { clock }
        runBlocking {
            controller.startSource(object : TelemetrySource {
                override val packets = flow {
                    for (p in head) { clock = p.atMs; emit(p) }
                    gate.await()
                    for (p in tail) { delay(16); clock = p.atMs; emit(p) }
                }
            })
            // the track question comes after the first full lap
            repeat(100) { if (controller.trackQuestion.value == null) Thread.sleep(50) }
            controller.confirmTrack()
            repeat(100) { if (controller.live.value.distanceM < 1200f) Thread.sleep(50) }
            Thread.sleep(500)
        }
        println("live: ${controller.live.value.distanceM} m, delta ${controller.live.value.deltaMs}, ref ${controller.reference.value?.timeMs}")
        shot("live", 1600, 900, frames = 340, frameMs = 33, start = { gate.complete(Unit) }) { LiveScreen(controller, SpeedUnit.KMH, cars, LiveLayout.Standard) {} }
        shot("live-race", 1600, 900) { LiveScreen(controller, SpeedUnit.KMH, cars, LiveLayout.Race) {} }
        scope.cancel()


        val debrief = Engineer.analyze(slow, fast)
        val comparison = repo.comparisonLap(4, 1, 3656, 14)
        val state = EngineerState.Ready(debrief, null, fast.last().timeMs)
        val corners = debrief.reports.map { it.corner }
        val laps = sessions.filter { it.trackId == 4L }.flatMap { repo.laps(it.id) }
        val vp = AnalysisViewport(slow, fast).also { v ->
            debrief.tips.firstOrNull()?.corner?.let { v.toggleCorner(it) }
            v.moveCursor((v.range.start + v.range.endInclusive) / 2f)
        }
        shot("analysis", 1600, 1000) {
            Column(Modifier.padding(8.dp)) {
                ChartToolbar(vp)
                DetailAnalysisView(vp, corners, laps, repo::samples, Modifier.fillMaxSize(), panel = { EngineerPanel(state, TipLanguage.DE, vp) })
            }
        }
        println("comparison $comparison")
    }
}
