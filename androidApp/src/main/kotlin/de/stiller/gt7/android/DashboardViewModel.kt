package de.stiller.gt7.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.stiller.gt7.db.Gt7Database
import de.stiller.gt7.session.SessionController
import de.stiller.gt7.storage.CarCatalog
import de.stiller.gt7.storage.DataTransfer
import de.stiller.gt7.storage.SessionRepository
import de.stiller.gt7.storage.appDataDir
import de.stiller.gt7.storage.createDriver
import de.stiller.gt7.storage.loadCarCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Holds the session across activity recreation, like desktopApp's main() does for the window. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val database = Gt7Database(createDriver())
    val repo = SessionRepository(database)
    val controller = SessionController(scope, repo) { System.currentTimeMillis() }
    val transfer = DataTransfer(database, appDataDir())

    private val _cars = MutableStateFlow(CarCatalog(""))
    val cars: StateFlow<CarCatalog> = _cars.asStateFlow()

    init {
        controller.start()
        scope.launch {
            try {
                _cars.value = loadCarCatalog()
            } catch (e: Exception) {
                println("[GT7] loading cars.csv failed: $e")
            }
        }
    }

    override fun onCleared() {
        controller.stopRecording()
        scope.cancel()
    }
}
