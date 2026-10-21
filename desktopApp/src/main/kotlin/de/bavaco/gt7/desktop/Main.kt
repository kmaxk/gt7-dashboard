package de.bavaco.gt7.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.bavaco.gt7.session.SessionController
import de.bavaco.gt7.session.StayAwake
import de.bavaco.gt7.storage.CarCatalog
import de.bavaco.gt7.storage.AwtFilePicker
import de.bavaco.gt7.storage.DataTransfer
import de.bavaco.gt7.storage.SessionRepository
import de.bavaco.gt7.storage.SleepBlocker
import de.bavaco.gt7.storage.appDataDir
import de.bavaco.gt7.storage.createDriver
import de.bavaco.gt7.db.Gt7Database
import de.bavaco.gt7.ui.App
import de.bavaco.gt7.storage.loadCarCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
fun main() = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)) }
    val database = remember { Gt7Database(createDriver()) }
    val repo = remember { SessionRepository(database) }
    val transfer = remember { DataTransfer(database, appDataDir()) }
    val controller = remember { SessionController(scope, repo) { System.currentTimeMillis() } }
    var cars by remember { mutableStateOf(CarCatalog("")) }
    val sleepBlocker = remember { SleepBlocker() }
    // while the PS5 sends (and two minutes after), neither the computer nor its display go to sleep
    LaunchedEffect(Unit) {
        val stayAwake = StayAwake()
        while (true) {
            sleepBlocker.set(stayAwake.update(controller.connection.value, System.currentTimeMillis()))
            delay(5_000)
        }
    }
    LaunchedEffect(Unit) {
        controller.start()
        try {
            cars = loadCarCatalog()
        } catch (e: Exception) {
            println("[GT7] loading cars.csv failed: $e")
        }
    }
    Window(onCloseRequest = {
        controller.stopRecording()
        sleepBlocker.set(false)
        scope.cancel()
        exitApplication()
    }, title = "GT7 Dashboard", icon = remember { BitmapPainter(useResource("app-icon.png", ::loadImageBitmap)) }) {
        App(controller, repo, cars, transfer, remember { AwtFilePicker() })
    }
}
