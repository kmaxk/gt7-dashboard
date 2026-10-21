package de.stiller.gt7.session

import de.stiller.gt7.storage.Driver
import de.stiller.gt7.storage.ImportResult
import de.stiller.gt7.storage.SessionRepository
import de.stiller.gt7.storage.validateDriverName
import de.stiller.gt7.telemetry.PacketRecorder
import de.stiller.gt7.telemetry.PacketStats
import de.stiller.gt7.telemetry.RawPacket
import de.stiller.gt7.telemetry.ReplayTelemetrySource
import de.stiller.gt7.telemetry.TelemetryPipeline
import de.stiller.gt7.telemetry.TelemetrySource
import de.stiller.gt7.telemetry.UdpTelemetrySource
import de.stiller.gt7.telemetry.discoverPs5
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Sink
import kotlin.concurrent.Volatile

sealed interface TrackQuestion {
    data class Suggest(val track: Track, val lastDrivenAtMs: Long) : TrackQuestion
    data object AskName : TrackQuestion
}

enum class ConnectionStatus { Idle, Searching, Waiting, Receiving }

private const val TICK_MS = 1_000L
private const val RECEIVING_MAX_AGE_MS = 2_000L
private const val HOST_SETTING = "ps5Host"
private const val ACTIVE_DRIVER_SETTING = "activeDriver"
private const val OPPONENT_SETTING = "opponent"
private const val OPPONENT_SELF = "self"

/**
 * Wires a telemetry source through [TelemetryPipeline] into one [SessionEngine] and persists its
 * events in [repo]. Handles track questions and the live reference lap.
 *
 * Designed for a single-threaded [scope], e.g. `CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))`;
 * engine and session state are additionally touched only under [lock]. Public actions are executed
 * asynchronously in [scope]. A failing source is logged and leaves the controller [ConnectionStatus.Idle].
 */
class SessionController(
    private val scope: CoroutineScope,
    private val repo: SessionRepository,
    private val nowMs: () -> Long,
) {
    private val engine = SessionEngine()
    private val lock = Mutex()

    val live: StateFlow<LiveState> = engine.live

    private val _stats = MutableStateFlow(PacketStats(0, 0L, null))
    val stats: StateFlow<PacketStats> = _stats.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionStatus.Idle)
    val connection: StateFlow<ConnectionStatus> = _connection.asStateFlow()

    private val _ps5Host = MutableStateFlow<String?>(null)
    val ps5Host: StateFlow<String?> = _ps5Host.asStateFlow()

    private val _trackQuestion = MutableStateFlow<TrackQuestion?>(null)
    val trackQuestion: StateFlow<TrackQuestion?> = _trackQuestion.asStateFlow()

    private val _recording = MutableStateFlow<String?>(null)
    /** Name of the file currently being recorded, null when not recording. */
    val recording: StateFlow<String?> = _recording.asStateFlow()

    private val _drivers = MutableStateFlow<List<Driver>>(emptyList())
    val drivers: StateFlow<List<Driver>> = _drivers.asStateFlow()

    private val _activeDriver = MutableStateFlow<Driver?>(null)
    val activeDriver: StateFlow<Driver?> = _activeDriver.asStateFlow()

    private val _opponent = MutableStateFlow<Opponent>(Opponent.Self)
    val opponent: StateFlow<Opponent> = _opponent.asStateFlow()

    private val _activeSession = MutableStateFlow<Long?>(null)
    /** The session being driven right now, null between sessions. */
    val activeSession: StateFlow<Long?> = _activeSession.asStateFlow()

    private val _reference = MutableStateFlow<ReferenceInfo?>(null)
    val reference: StateFlow<ReferenceInfo?> = _reference.asStateFlow()

    private val _trackLine = MutableStateFlow<List<LapSample>?>(null)
    /** A driven lap on the current track (anyone's fastest), so the live map shows it before an own lap. */
    val trackLine: StateFlow<List<LapSample>?> = _trackLine.asStateFlow()

    private val _tracksVersion = MutableStateFlow(0)
    /** Bumped after every track change (rename, delete, line), so the tracks tab reloads. */
    val tracksVersion: StateFlow<Int> = _tracksVersion.asStateFlow()

    private val _driversWithSessions = MutableStateFlow<Set<Long>>(emptySet())
    /** Drivers that own sessions; deleting them also deletes their laps. */
    val driversWithSessions: StateFlow<Set<Long>> = _driversWithSessions.asStateFlow()

    private var sourceJob: Job? = null
    private var discoverJob: Job? = null
    @Volatile private var hasSource = false
    @Volatile private var recorder: PacketRecorder? = null

    // per-session state, guarded by lock
    private var sessionId: Long? = null
    private var trackId: Long? = null
    private var outline: FloatArray? = null
    private var referenceMs: Int? = null
    /** The reference is the driver's marked reference lap: faster laps don't replace it. */
    private var referencePinned = false
    private var carCode: Int? = null
    /** Track and outline handed from a session ended by a driver switch to the next session. */
    private var carryTrack: Pair<Long, FloatArray?>? = null
    /** Known track recognised from the position while [trackId] is still unknown; shown, not assigned. */
    private var locatedTrack: Long? = null
    private var candidateLines: Map<Long, List<LapSample>> = emptyMap()
    private val locator = TrackLocator()

    init {
        scope.launch {
            lock.withLock { loadDrivers() }
            while (true) {
                delay(TICK_MS)
                lock.withLock {
                    handle(engine.onTick(nowMs()))
                    locateTrack()
                }
                updateConnection()
            }
        }
    }

    /** Connects to the stored PS5 host, or discovers one if none is stored. */
    fun start() {
        val host = db("load host") { repo.setting(HOST_SETTING) }
        if (host != null) connect(host) else discover()
    }

    fun connect(host: String) {
        db("save host") { repo.setSetting(HOST_SETTING, host) }
        _ps5Host.value = host
        startSource(UdpTelemetrySource(host, nowMs = nowMs))
    }

    fun discover() {
        val previousSource = sourceJob
        val previousDiscovery = discoverJob
        hasSource = false
        _connection.value = ConnectionStatus.Searching
        discoverJob = scope.launch {
            // discovery binds the receive port the running source (or an earlier discovery) holds
            previousDiscovery?.cancelAndJoin()
            previousSource?.cancelAndJoin()
            val host = try {
                discoverPs5()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("discovery failed", e)
                null
            }
            if (host != null) connect(host) else _connection.value = ConnectionStatus.Idle
        }
    }

    fun startReplay(recording: List<RawPacket>) {
        stopRecording() // replayed packets must not be recorded again
        startSource(ReplayTelemetrySource(recording, nowMs))
    }

    /** Replaces the active source; the engine and its session state survive the switch. */
    fun startSource(source: TelemetrySource) {
        val previousSource = sourceJob
        val previousDiscovery = discoverJob
        hasSource = true
        if (_connection.value != ConnectionStatus.Receiving) _connection.value = ConnectionStatus.Waiting
        sourceJob = scope.launch {
            // both may hold the receive port: wait until it is released before binding again
            previousDiscovery?.cancelAndJoin()
            previousSource?.cancelAndJoin()
            try {
                collect(source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("telemetry source failed", e)
                if (sourceJob === coroutineContext[Job]) {
                    hasSource = false
                    _connection.value = ConnectionStatus.Idle
                }
            }
        }
    }

    private suspend fun collect(source: TelemetrySource) {
        val pipeline = TelemetryPipeline(source) { recorder }
        try {
            coroutineScope {
                val statsJob = launch {
                    pipeline.stats.collect {
                        _stats.value = it
                        updateConnection()
                    }
                }
                pipeline.frames.collect { tf ->
                    lock.withLock {
                        // GT7 loads a track: the next session may be elsewhere, so a carried track no longer applies
                        if (tf.frame.loading) carryTrack = null
                        handle(engine.onFrame(tf.frame, tf.atMs))
                        if (sessionId != null && trackId == null && !tf.frame.loading) locator.add(tf.frame.x, tf.frame.z)
                    }
                }
                statsJob.cancel()
            }
        } finally {
            _stats.value = pipeline.stats.value
        }
    }

    fun startRecording(sink: Sink, name: String) {
        val old = recorder
        recorder = PacketRecorder(sink)
        _recording.value = name
        old?.closeQuietly()
    }

    fun stopRecording() {
        val old = recorder
        recorder = null
        _recording.value = null
        old?.closeQuietly()
    }

    fun confirmTrack() = withState {
        val q = _trackQuestion.value as? TrackQuestion.Suggest ?: return@withState
        val session = sessionId ?: return@withState
        db("assign track") { repo.assignTrack(session, q.track.id) }
        trackId = q.track.id
        _trackQuestion.value = null
        loadTrackLine()
        loadReference()
    }

    fun rejectSuggestion() = withState {
        if (_trackQuestion.value is TrackQuestion.Suggest) _trackQuestion.value = TrackQuestion.AskName
    }

    fun nameTrack(name: String) = withState {
        val session = sessionId ?: return@withState
        val shape = outline ?: return@withState
        if (trackId != null) return@withState
        val id = db("create track") { repo.createTrack(name, shape) } ?: return@withState
        db("assign track") { repo.assignTrack(session, id) }
        trackId = id
        _trackQuestion.value = null
        loadTrackLine()
    }

    private fun withState(block: () -> Unit) {
        scope.launch { lock.withLock { block() } }
    }

    private fun handle(events: List<SessionEvent>) {
        for (e in events) when (e) {
            is SessionEvent.Started -> {
                val session = db("create session") { repo.createSession(activeDriverId(), e.carCode, e.atMs) }
                sessionId = session
                _activeSession.value = session
                carCode = e.carCode
                _trackQuestion.value = null
                _driversWithSessions.value = db("load drivers with sessions") { repo.driversWithSessions() } ?: _driversWithSessions.value
                val carried = carryTrack
                carryTrack = null
                if (carried != null && session != null) {
                    trackId = carried.first
                    outline = carried.second
                    db("assign track") { repo.assignTrack(session, carried.first) }
                    loadTrackLine()
                    loadReference()
                } else {
                    trackId = null
                    outline = null
                    _trackLine.value = null
                    clearReference()
                    startLocating()
                }
            }
            is SessionEvent.LapCompleted -> onLap(e.lap)
            is SessionEvent.Ended -> {
                _activeSession.value = null
                sessionId?.let { id -> db("end session") { repo.endSession(id, e.atMs) } }
            }
        }
    }

    private fun onLap(lap: CompletedLap) {
        val session = sessionId
        if (session != null) db("save lap") { repo.saveLap(session, lap) }
        if (!lap.isValid) return
        val ref = referenceMs
        val driver = _activeDriver.value
        if (_opponent.value == Opponent.Self && driver != null && !referencePinned && (ref == null || lap.timeMs < ref)) {
            setReference(lap.samples, lap.timeMs)
            _reference.value = ReferenceInfo(driver, true, lap.timeMs, carCode ?: 0, nowMs(), true, lap.samples)
        }
        if (session != null && trackId == null && outline == null && lap.samples.size >= 2) askForTrack(lap.samples)
    }

    private fun askForTrack(samples: List<LapSample>) {
        val shape = TrackMatcher.outlineOf(samples)
        outline = shape
        val tracks = db("load tracks") { repo.tracks() } ?: emptyList()
        // the outline decides; the position match helps when the outline alone is not close enough
        val match = TrackMatcher.findMatch(shape, tracks) ?: tracks.find { it.id == locatedTrack }
        _trackQuestion.value = if (match == null) {
            TrackQuestion.AskName
        } else {
            TrackQuestion.Suggest(match, db("load last drive") { repo.lastSessionStartForTrack(match.id) } ?: 0L)
        }
    }

    fun renameTrack(id: Long, name: String) = withState {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return@withState
        db("rename track") { repo.renameTrack(id, trimmed) }
        _tracksVersion.value++
    }

    /** Deletes the track; its laps stay without a track. The running session forgets it, too. */
    fun deleteTrack(id: Long) = withState {
        db("delete track") { repo.deleteTrack(id) }
        if (carryTrack?.first == id) carryTrack = null
        if (trackId == id) {
            trackId = null
            outline = null
            _trackLine.value = null
            clearReference()
            startLocating()
        } else if (trackId == null) {
            if (locatedTrack == id) {
                locatedTrack = null
                _trackLine.value = null
            }
            reloadCandidates()
        }
        _tracksVersion.value++
    }

    /** Chooses [lapId] as the track's line; null returns to the fastest valid lap. */
    fun setTrackLine(trackId: Long, lapId: Long?) = withState {
        db("set track line") { repo.setTrackLine(trackId, lapId) }
        if (this.trackId == trackId) {
            loadTrackLine()
        } else if (this.trackId == null) {
            reloadCandidates()
            if (locatedTrack == trackId) _trackLine.value = candidateLines[trackId]
        }
        _tracksVersion.value++
    }

    /** Runs [block] while no telemetry is processed, e.g. to read the database consistently. */
    suspend fun <T> exclusive(block: () -> T): T = lock.withLock { block() }

    /** Runs [import] exclusively, then reloads what it may have changed: drivers, track lines, reference. */
    suspend fun importData(import: () -> ImportResult): ImportResult = lock.withLock {
        val result = import()
        refreshDrivers()
        loadTrackLine()
        loadReference()
        if (trackId == null) reloadCandidates()
        _tracksVersion.value++
        result
    }

    /** Restarts position matching for a session without track. */
    private fun startLocating() {
        locatedTrack = null
        locator.reset()
        if (trackId == null) reloadCandidates()
    }

    private fun reloadCandidates() {
        candidateLines = (db("load tracks") { repo.tracks() } ?: emptyList())
            .mapNotNull { t -> db("load track line") { repo.trackLine(t.id) }?.let { t.id to it } }
            .toMap()
        locator.setCandidates(candidateLines)
    }

    private fun locateTrack() {
        if (sessionId == null || trackId != null) return
        val found = locator.locate(locatedTrack)
        if (found == locatedTrack) return
        locatedTrack = found
        _trackLine.value = found?.let { candidateLines[it] }
    }

    fun selectDriver(id: Long) = withState {
        val driver = _drivers.value.find { it.id == id } ?: return@withState
        if (driver == _activeDriver.value) return@withState
        db("save active driver") { repo.setSetting(ACTIVE_DRIVER_SETTING, id.toString()) }
        _activeDriver.value = driver
        if ((_opponent.value as? Opponent.Of)?.driver?.id == id) setOpponent(Opponent.Self)
        val track = trackId
        val ended = engine.endSession(nowMs())
        if (ended.isNotEmpty()) {
            carryTrack = track?.let { it to outline }
            handle(ended)
        }
    }

    fun selectOpponent(opponent: Opponent) = withState {
        val normalized = if (opponent is Opponent.Of && opponent.driver.id == _activeDriver.value?.id) Opponent.Self else opponent
        setOpponent(normalized)
        loadReference()
    }

    fun createDriver(name: String) = withState {
        val valid = validateDriverName(name, _drivers.value) ?: return@withState
        db("create driver") { repo.createDriver(valid) }
        refreshDrivers()
    }

    fun renameDriver(id: Long, name: String) = withState {
        val valid = validateDriverName(name, _drivers.value.filter { it.id != id }) ?: return@withState
        db("rename driver") { repo.renameDriver(id, valid) }
        refreshDrivers()
    }

    /** Deletes [id] with all its laps; never the active or the last driver. */
    fun deleteDriver(id: Long) = withState {
        if (id == _activeDriver.value?.id || _drivers.value.size <= 1) return@withState
        val wasOpponent = (_opponent.value as? Opponent.Of)?.driver?.id == id
        db("delete driver") { repo.deleteDriver(id) }
        refreshDrivers()
        // their lap may have been the map line or the reference
        loadTrackLine()
        if (wasOpponent) {
            setOpponent(Opponent.Self)
            loadReference()
        }
    }

    /** Deletes a session that is not being driven; reloads track line and reference, which may have been its laps. */
    suspend fun deleteSession(id: Long): Boolean = lock.withLock {
        if (id == _activeSession.value) return@withLock false
        db("delete session") { repo.deleteSession(id) } ?: return@withLock false
        loadTrackLine()
        loadReference()
        true
    }

    /** Marks or unmarks [lapId] as its driver's reference lap on its track and reloads the live reference. */
    suspend fun setReferenceLap(lapId: Long, on: Boolean) = lock.withLock {
        db("set reference lap") { repo.setReferenceLap(lapId, on) }
        loadReference()
    }

    private fun loadDrivers() {
        val stored = db("load active driver") { repo.setting(ACTIVE_DRIVER_SETTING) }?.toLongOrNull()
        val opponent = db("load opponent") { repo.setting(OPPONENT_SETTING) }
        refreshDrivers(stored, opponent)
    }

    /** Reloads drivers; active driver and opponent are re-resolved by id, falling back to the first driver / Self. */
    private fun refreshDrivers(
        activeId: Long? = _activeDriver.value?.id,
        opponentSetting: String? = (_opponent.value as? Opponent.Of)?.driver?.id?.toString() ?: OPPONENT_SELF,
    ) {
        db("ensure default driver") { repo.ensureDefaultDriver() }
        val all = db("load drivers") { repo.drivers() } ?: return
        _drivers.value = all
        val active = all.find { it.id == activeId } ?: all.first()
        _activeDriver.value = active
        val opponent = opponentSetting?.toLongOrNull()?.let { id -> all.find { it.id == id && it.id != active.id } }
        _opponent.value = if (opponent != null) Opponent.Of(opponent) else Opponent.Self
        _driversWithSessions.value = db("load drivers with sessions") { repo.driversWithSessions() } ?: emptySet()
    }

    private fun setOpponent(opponent: Opponent) {
        val value = (opponent as? Opponent.Of)?.driver?.id?.toString() ?: OPPONENT_SELF
        db("save opponent") { repo.setSetting(OPPONENT_SETTING, value) }
        _opponent.value = opponent
    }

    private fun activeDriverId(): Long =
        _activeDriver.value?.id ?: db("ensure default driver") { repo.ensureDefaultDriver() }?.id ?: 0L

    private fun loadTrackLine() {
        _trackLine.value = trackId?.let { track -> db("load track line") { repo.trackLine(track) } }
    }

    /** Loads the opponent's best lap on the current track (same car first) as live reference. */
    private fun loadReference() {
        val track = trackId
        val car = carCode
        val active = _activeDriver.value
        if (track == null || car == null || active == null) return clearReference()
        val owner = when (val o = _opponent.value) {
            Opponent.Self -> active
            is Opponent.Of -> o.driver
        }
        val best = db("load best lap") { repo.bestLap(track, owner.id, car) }
        if (best == null || best.samples.isEmpty()) return clearReference()
        setReference(best.samples, best.timeMs)
        referencePinned = best.isReference
        _reference.value = ReferenceInfo(owner, owner == active, best.timeMs, best.carCode, best.drivenAtMs, best.carCode == car, best.samples)
    }

    private fun clearReference() {
        setReference(null, null)
        referencePinned = false
        _reference.value = null
    }

    private fun setReference(samples: List<LapSample>?, timeMs: Int?) {
        referenceMs = timeMs
        engine.setReference(samples, timeMs)
    }

    private fun updateConnection() {
        if (!hasSource) return
        val last = _stats.value.lastPacketAtMs
        _connection.value = if (last != null && nowMs() - last <= RECEIVING_MAX_AGE_MS) {
            ConnectionStatus.Receiving
        } else {
            ConnectionStatus.Waiting
        }
    }

    private fun PacketRecorder.closeQuietly() {
        try {
            close()
        } catch (e: Exception) {
            log("closing recording failed", e)
        }
    }

    /** Storage failures must not stop live telemetry: log and continue with null. */
    private inline fun <T> db(what: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("DB error ($what)", e)
        null
    }

    private fun log(what: String, e: Exception) = println("[GT7] $what: $e")
}
