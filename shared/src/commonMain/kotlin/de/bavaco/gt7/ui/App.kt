package de.bavaco.gt7.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.bavaco.gt7.session.SessionController
import de.bavaco.gt7.storage.CarCatalog
import de.bavaco.gt7.storage.DataTransfer
import de.bavaco.gt7.storage.FilePicker
import de.bavaco.gt7.storage.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TABS = listOf("Live", "Analyse", "Bestenliste", "Strecken", "Autos", "Einstellungen")

@Composable
fun App(
    controller: SessionController, repo: SessionRepository, carList: CarCatalog, transfer: DataTransfer, files: FilePicker,
    /** Phones: below 840 dp width or 480 dp height the layout turns compact. The desktop never does. */
    compactAllowed: Boolean = false,
    /** Voice for the live tips; null where the platform has none (the setting is then hidden). */
    speaker: Speaker? = null,
    /** True while the live tab is shown, e.g. to keep a phone's screen on. */
    onLiveVisible: (Boolean) -> Unit = {},
) {
    Gt7Theme {
        Surface(Modifier.fillMaxSize().carbon(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = RaceColors.TextPrimary) {
            var tab by remember { mutableStateOf(0) }
            LaunchedEffect(tab) { onLiveVisible(tab == 0) }
            var liveLayout by remember { mutableStateOf(LiveLayout.entries.firstOrNull { it.name == repo.setting("liveLayout") } ?: LiveLayout.Standard) }
            var unit by remember { mutableStateOf(if (repo.setting("unit") == "MPH") SpeedUnit.MPH else SpeedUnit.KMH) }
            var tipLanguage by remember { mutableStateOf(tipLanguageOf(repo.setting(TIP_LANGUAGE_SETTING))) }
            var coachEnabled by remember { mutableStateOf(repo.setting(COACH_SETTING) != "off") }
            var speechEnabled by remember { mutableStateOf(repo.setting(SPEECH_SETTING) == "on") }
            val scope = rememberCoroutineScope()
            val speechVoices = remember { mutableStateMapOf<TipLanguage, String?>().apply { TipLanguage.entries.forEach { put(it, repo.setting(speechVoiceSetting(it))) } } }
            if (speaker != null) {
                val currentSpeech by rememberUpdatedState(coachEnabled && speechEnabled)
                val currentLanguage by rememberUpdatedState(tipLanguage)
                val currentVoice by rememberUpdatedState(speechVoices[tipLanguage])
                LaunchedEffect(controller, speaker) {
                    var spoken: Int? = null
                    controller.coachTip.collect { tip ->
                        // once per corner: the tip stays up until the apex
                        if (tip == null) spoken = null
                        else if (tip.report.corner.number != spoken) {
                            spoken = tip.report.corner.number
                            if (currentSpeech) speaker.speak(EngineerText.spoken(tip.report, currentLanguage), currentLanguage, currentVoice)
                        }
                    }
                }
                LaunchedEffect(controller, speaker) {
                    controller.raceCall.collect { call ->
                        if (currentSpeech) speaker.speak(EngineerText.spoken(call, currentLanguage), currentLanguage, currentVoice)
                    }
                }
            }
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
                                TABS.forEachIndexed { i, title ->
                                    Tab(
                                        selected = tab == i, onClick = { tab = i; tabsExpanded = false },
                                        selectedContentColor = RaceColors.Menu, unselectedContentColor = RaceColors.TextPrimary,
                                        text = { Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                                    )
                                }
                            }
                            val indicator: @Composable TabIndicatorScope.() -> Unit = {
                                TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab, matchContentSize = true), width = Dp.Unspecified, color = RaceColors.Menu)
                            }
                            if (compact) PrimaryScrollableTabRow(selectedTabIndex = tab, indicator = indicator, tabs = tabs)
                            else PrimaryTabRow(selectedTabIndex = tab, indicator = indicator, tabs = tabs)
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            when (tab) {
                                0 -> LiveScreen(controller, unit, cars, liveLayout, tipLanguage, coachEnabled) {
                                    liveLayout = it
                                    scope.launch(Dispatchers.Default) { repo.setSetting("liveLayout", it.name) }
                                }
                                1 -> AnalysisScreen(controller, repo, cars, sessionOverview, tipLanguage, files)
                                2 -> LeaderboardScreen(repo, cars)
                                3 -> TracksScreen(controller, repo)
                                4 -> CarsScreen(repo, cars, reloadCars)
                                else -> SettingsScreen(
                                    controller, transfer, files, unit, tipLanguage, coachEnabled, onImported = reloadCars,
                                    speech = speaker?.let { SpeechSettings(speechEnabled, it.voices(tipLanguage), speechVoices[tipLanguage]) },
                                    onVoiceChange = { v ->
                                        speechVoices[tipLanguage] = v
                                        val key = speechVoiceSetting(tipLanguage)
                                        scope.launch(Dispatchers.Default) { repo.setSetting(key, v.orEmpty()) }
                                    },
                                    onSpeechChange = {
                                        speechEnabled = it
                                        scope.launch(Dispatchers.Default) { repo.setSetting(SPEECH_SETTING, if (it) "on" else "off") }
                                    },
                                    onCoachChange = {
                                        coachEnabled = it
                                        scope.launch(Dispatchers.Default) { repo.setSetting(COACH_SETTING, if (it) "on" else "off") }
                                    },
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
