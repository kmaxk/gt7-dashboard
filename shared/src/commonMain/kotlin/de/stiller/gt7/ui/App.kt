package de.stiller.gt7.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.stiller.gt7.session.SessionController
import de.stiller.gt7.storage.CarCatalog
import de.stiller.gt7.storage.DataTransfer
import de.stiller.gt7.storage.FilePicker
import de.stiller.gt7.storage.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TABS = listOf("Live", "Analyse", "Bestenliste", "Strecken", "Autos", "Einstellungen")

@Composable
fun App(
    controller: SessionController, repo: SessionRepository, carList: CarCatalog, transfer: DataTransfer, files: FilePicker,
    /** Phones: below 840 dp width or 480 dp height the layout turns compact. The desktop never does. */
    compactAllowed: Boolean = false,
    /** True while the live tab is shown, e.g. to keep a phone's screen on. */
    onLiveVisible: (Boolean) -> Unit = {},
) {
    Gt7Theme {
        Surface(Modifier.fillMaxSize().carbon(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = RaceColors.TextPrimary) {
            var tab by remember { mutableStateOf(0) }
            LaunchedEffect(tab) { onLiveVisible(tab == 0) }
            var liveLayout by remember { mutableStateOf(if (repo.setting("liveLayout") == LiveLayout.Race.name) LiveLayout.Race else LiveLayout.Standard) }
            var unit by remember { mutableStateOf(if (repo.setting("unit") == "MPH") SpeedUnit.MPH else SpeedUnit.KMH) }
            var tipLanguage by remember { mutableStateOf(tipLanguageOf(repo.setting(TIP_LANGUAGE_SETTING))) }
            val scope = rememberCoroutineScope()
            // the list with the own names; reloaded after renaming and importing
            var cars by remember(carList) { mutableStateOf(carList.withCustom(repo.carNames())) }
            val reloadCars: () -> Unit = {
                scope.launch { cars = carList.withCustom(withContext(Dispatchers.Default) { repo.carNames() }) }
            }
            val question by controller.trackQuestion.collectAsState()
            question?.let {
                TrackDialog(it, onConfirm = controller::confirmTrack, onReject = controller::rejectSuggestion, onName = controller::nameTrack)
            }
            var tabsExpanded by remember { mutableStateOf(false) }
            val sessionOverview = remember { SessionOverviewState() }
            // Android draws edge to edge: keep clear of status bar, navigation bar and camera cutout (all zero on the desktop)
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val compact = compactAllowed && isCompact(maxWidth.value, maxHeight.value)
                CompositionLocalProvider(LocalCompact provides compact) {
                    Column(Modifier.fillMaxSize()) {
                        if (tabRowVisible(compact, liveTab = tab == 0, expanded = tabsExpanded)) {
                            val tabs: @Composable () -> Unit = {
                                TABS.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i; tabsExpanded = false }, text = { Text(title) }) }
                            }
                            if (compact) PrimaryScrollableTabRow(selectedTabIndex = tab, tabs = tabs) else PrimaryTabRow(selectedTabIndex = tab, tabs = tabs)
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            when (tab) {
                                0 -> LiveScreen(controller, unit, cars, liveLayout) {
                                    liveLayout = it
                                    scope.launch(Dispatchers.Default) { repo.setSetting("liveLayout", it.name) }
                                }
                                1 -> AnalysisScreen(controller, repo, cars, sessionOverview, tipLanguage, files)
                                2 -> LeaderboardScreen(repo, cars)
                                3 -> TracksScreen(controller, repo)
                                4 -> CarsScreen(repo, cars, reloadCars)
                                else -> SettingsScreen(
                                    controller, transfer, files, unit, tipLanguage, onImported = reloadCars,
                                    onTipLanguageChange = {
                                        tipLanguage = it
                                        scope.launch(Dispatchers.Default) { repo.setSetting(TIP_LANGUAGE_SETTING, it.name) }
                                    },
                                ) {
                                    unit = it
                                    scope.launch(Dispatchers.Default) { repo.setSetting("unit", it.name) }
                                }
                            }
                            // phone live view without tab row: a small button brings it back
                            if (compact && tab == 0) {
                                FilledTonalButton(onClick = { tabsExpanded = !tabsExpanded }, Modifier.align(Alignment.BottomStart).padding(8.dp), shape = RaceButtonShape) { Text("☰") }
                            }
                        }
                    }
                }
            }
        }
    }
}
