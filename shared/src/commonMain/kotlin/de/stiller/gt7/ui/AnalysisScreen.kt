package de.stiller.gt7.ui

import androidx.compose.material3.FilterChip
import de.stiller.gt7.storage.FilePicker
import de.stiller.gt7.session.SessionController
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.stiller.gt7.session.Corner
import de.stiller.gt7.session.Engineer
import de.stiller.gt7.session.LapSample
import de.stiller.gt7.session.deltaCurve
import de.stiller.gt7.storage.CarCatalog
import de.stiller.gt7.storage.LapSummary
import de.stiller.gt7.storage.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AnalysisScreen(
    controller: SessionController,
    repo: SessionRepository,
    cars: CarCatalog,
    overview: SessionOverviewState,
    language: TipLanguage,
    files: FilePicker,
) {
    val sessions by remember { repo.sessions() }.collectAsState(emptyList())
    var openId by remember { mutableStateOf<Long?>(null) }
    var picked by remember { mutableStateOf<List<LapSummary>>(emptyList()) }
    var filterOff by remember { mutableStateOf(false) }
    var laps by remember { mutableStateOf<List<LapSummary>>(emptyList()) }
    var samplesA by remember { mutableStateOf<List<LapSample>>(emptyList()) }
    var samplesB by remember { mutableStateOf<List<LapSample>>(emptyList()) }
    var moreChannels by remember { mutableStateOf(false) }
    var engineer by remember { mutableStateOf<EngineerState>(EngineerState.Computing) }
    val scope = rememberCoroutineScope()
    val activeSession by controller.activeSession.collectAsState()
    // bumped after a lap changed (reference star), reloads the lap list
    var lapsVersion by remember { mutableStateOf(0) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val capture = rememberGraphicsLayer()

    LaunchedEffect(openId, lapsVersion) {
        laps = openId?.let { id -> withContext(Dispatchers.Default) { repo.laps(id) } } ?: emptyList()
    }
    val aId = picked.getOrNull(0)?.id
    val bId = picked.getOrNull(1)?.id
    LaunchedEffect(aId) {
        samplesA = aId?.let { withContext(Dispatchers.Default) { repo.samples(it) } } ?: emptyList()
    }
    LaunchedEffect(bId) {
        samplesB = bId?.let { withContext(Dispatchers.Default) { repo.samples(it) } } ?: emptyList()
    }

    val lapB = samplesB.takeIf { bId != null }
    val viewport = remember(samplesA, lapB) { samplesA.takeIf { it.isNotEmpty() }?.let { AnalysisViewport(it, lapB) } }
    LaunchedEffect(samplesA, lapB) {
        val lapA = picked.firstOrNull() ?: return@LaunchedEffect
        if (samplesA.isEmpty()) return@LaunchedEffect
        engineer = EngineerState.Computing
        val pickedB = picked.getOrNull(1)
        engineer = withContext(Dispatchers.Default) {
            if (lapB != null) return@withContext EngineerState.Ready(Engineer.analyze(samplesA, lapB), null, pickedB?.timeMs)
            val session = sessions.firstOrNull { it.id == lapA.sessionId }
            val trackId = session?.trackId ?: return@withContext EngineerState.NoTrack
            val comparison = repo.comparisonLap(trackId, session.driverId, session.carCode, lapA.id)
                ?: return@withContext EngineerState.NoComparison
            EngineerState.Ready(Engineer.analyze(samplesA, repo.samples(comparison.lapId)), comparison, null)
        }
    }

    val sessionA = picked.firstOrNull()?.let { a -> sessions.firstOrNull { it.id == a.sessionId } }
    val open = sessions.firstOrNull { it.id == openId }
    if (open == null) {
        // with lap A picked the overview offers only the sessions lap B may come from
        SessionOverview(
            sessions, cars, overview, restrictTo = sessionA.takeIf { !filterOff },
            onClearRestriction = { filterOff = true }, onOpen = { openId = it }, modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val best = open.bestLapMs
    val sessionHeader: @Composable () -> Unit = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openId = null }) { Text("← Sitzungen") }
                Text(sessionLine(open, cars), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            SessionActions(
                open, running = open.id == activeSession,
                onRename = { name -> scope.launch(Dispatchers.Default) { repo.renameSession(open.id, name) } },
                onDelete = {
                    scope.launch {
                        if (controller.deleteSession(open.id)) {
                            picked = picked.filter { it.sessionId != open.id }
                            if (picked.isEmpty()) filterOff = false
                            openId = null
                        }
                    }
                },
            )
        }
    }
    val onStar: ((LapSummary) -> Unit)? = if (open.trackId == null) null else { lap ->
        scope.launch {
            controller.setReferenceLap(lap.id, !lap.isReference)
            lapsVersion++
        }
    }
    val lapList: @Composable (Modifier) -> Unit = { m ->
        LapList(laps, best, picked, { picked = toggleLap(picked, it, sessions); if (picked.isEmpty()) filterOff = false }, m, onStar)
    }
    val content: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp)) }
            val lapA = picked.firstOrNull()
            if (lapA == null || viewport == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Wähle eine oder zwei Runden aus (zweite Runde nur von derselben Strecke).")
                }
            } else {
                val pickedB = picked.getOrNull(1)
                // the analysis also draws into [capture], which the PNG export saves
                val captured = Modifier.fillMaxSize().drawWithContent {
                    capture.record { this@drawWithContent.drawContent() }
                    drawLayer(capture)
                }
                CockpitAnalysis(
                    viewport, engineer, language,
                    CockpitLaps(lapA, sessions.firstOrNull { it.id == lapA.sessionId }, pickedB, pickedB?.let { b -> sessions.firstOrNull { it.id == b.sessionId } }),
                    laps, repo::samples, cars::name, moreChannels, { moreChannels = it },
                    toolbar = {
                        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            ChartToolbar(viewport)
                            ExportActions(lapA, sessions.firstOrNull { it.id == lapA.sessionId }, samplesA, cars, files, capture) { exportMessage = it }
                        }
                    },
                    modifier = captured,
                )
            }
        }
    }

    if (LocalCompact.current) {
        // phone: laps on top, the cockpit below scrolls on its own
        Column(Modifier.fillMaxSize()) {
            sessionHeader()
            lapList(Modifier.fillMaxWidth().height(COMPACT_LIST_HEIGHT))
            content(Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(340.dp).fillMaxHeight()) {
                sessionHeader()
                lapList(Modifier.weight(1f))
            }
            content(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

private val COMPACT_LIST_HEIGHT = 200.dp
