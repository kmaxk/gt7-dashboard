package de.bavaco.gt7.session

import de.bavaco.gt7.telemetry.TelemetryFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

private const val SESSION_TIMEOUT_MS = 30_000L
private const val MAX_STEP_MS = 100L
private const val LAST_LAP_WAIT_MS = 2_000L
private const val MAX_TIME_MISMATCH_MS = 2_000
private const val SAMPLE_INTERVAL_MS = 100
private const val BREAK_CONFIRM_MS = 500L

/**
 * Turns telemetry frames into sessions, laps and a live state. Not thread-safe: one caller feeds
 * frames ([onFrame]) and clock ticks ([onTick]) sequentially.
 */
class SessionEngine {
    private val _live = MutableStateFlow(IDLE)
    val live: StateFlow<LiveState> = _live.asStateFlow()

    private var reference: List<LapSample>? = null
    private var referenceMs: Int? = null

    // session state
    private var active = false
    private var carCode = 0
    private var lastFrameAtMs = 0L
    private var prevPacketId = 0
    private var prevLastLapMs = 0
    private var currentLap = 0
    private val validFuelUsed = mutableListOf<Float>()
    private var pending: PendingLap? = null
    private var breakSinceMs: Long? = null

    // current lap state
    private var lapStartObserved = false
    private var lapMeasuredMs = 0L
    private var lapDistanceM = 0f
    private var lapStartFuel = 0f
    private var lapInvalid = false
    private var lapSamples = mutableListOf<LapSample>()

    fun setReference(samples: List<LapSample>?, timeMs: Int?) {
        reference = samples
        referenceMs = timeMs
    }

    fun onFrame(frame: TelemetryFrame, atMs: Long): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        // GT7 sends single loading or foreign-car frames mid-race: only a lasting break ends the session
        val breaking = active && (frame.loading || frame.carCode != carCode)
        val since = if (breaking) breakSinceMs ?: atMs else null
        breakSinceMs = since
        if (since != null && atMs - since >= BREAK_CONFIRM_MS) end(atMs, events)
        if (!active) {
            if (frame.onTrack && !frame.loading) start(frame, atMs, events)
        } else if (since == null) {
            advance(frame, atMs, events)
        }
        publish(frame)
        return events
    }

    fun onTick(atMs: Long): List<SessionEvent> {
        if (!active) return emptyList()
        val events = mutableListOf<SessionEvent>()
        if (atMs - lastFrameAtMs > SESSION_TIMEOUT_MS) {
            stop(atMs, events)
        } else {
            expirePending(atMs, events)
        }
        return events
    }

    /** Ends the running session like a timeout (pending lap finalized); the next frame starts a new one. */
    fun endSession(atMs: Long): List<SessionEvent> {
        if (!active) return emptyList()
        val events = mutableListOf<SessionEvent>()
        stop(atMs, events)
        return events
    }

    private fun stop(atMs: Long, events: MutableList<SessionEvent>) {
        end(atMs, events)
        _live.value = _live.value.copy(sessionActive = false, deltaMs = null, predictedLapMs = null)
    }

    private fun start(frame: TelemetryFrame, atMs: Long, events: MutableList<SessionEvent>) {
        active = true
        carCode = frame.carCode
        currentLap = frame.currentLap
        validFuelUsed.clear()
        pending = null
        resetLap(frame, observed = false)
        remember(frame, atMs)
        events += SessionEvent.Started(frame.carCode, atMs)
    }

    private fun end(atMs: Long, events: MutableList<SessionEvent>) {
        pending?.let { events += finalizeFallback(it) }
        pending = null
        active = false
        breakSinceMs = null
        events += SessionEvent.Ended(atMs)
    }

    private fun advance(frame: TelemetryFrame, atMs: Long, events: MutableList<SessionEvent>) {
        // The step up to this frame belongs to the lap that was running before it.
        val dt = (atMs - lastFrameAtMs).coerceAtLeast(0)
        lapMeasuredMs += dt
        if (frame.onTrack && !frame.paused) lapDistanceM += frame.speedMps * minOf(dt, MAX_STEP_MS) / 1000f
        if (frame.paused || frame.packetId < prevPacketId) lapInvalid = true

        pending?.let { if (frame.lastLapMs != it.lastLapMsBefore) events += finalize(it, frame.lastLapMs) }
        expirePending(atMs, events)

        when {
            frame.currentLap > currentLap -> {
                pending?.let { events += finalizeFallback(it) }
                pending = null
                if (lapStartObserved) {
                    val p = PendingLap(
                        number = currentLap,
                        measuredMs = lapMeasuredMs.toInt(),
                        fuelUsed = lapStartFuel - frame.fuelLevel,
                        invalid = lapInvalid,
                        samples = lapSamples,
                        lastLapMsBefore = prevLastLapMs,
                        incrementAtMs = atMs,
                    )
                    if (frame.lastLapMs != p.lastLapMsBefore) events += finalize(p, frame.lastLapMs) else pending = p
                }
                currentLap = frame.currentLap
                resetLap(frame, observed = true)
            }
            frame.currentLap < currentLap -> { // race restarted: lap start not observed
                currentLap = frame.currentLap
                resetLap(frame, observed = false)
            }
            else -> sample(frame)
        }
        remember(frame, atMs)
    }

    private fun expirePending(atMs: Long, events: MutableList<SessionEvent>) {
        val p = pending ?: return
        if (atMs - p.incrementAtMs >= LAST_LAP_WAIT_MS) events += finalizeFallback(p)
    }

    private fun finalize(p: PendingLap, timeMs: Int): SessionEvent.LapCompleted {
        pending = null
        val valid = !p.invalid && abs(p.measuredMs - timeMs) <= MAX_TIME_MISMATCH_MS
        if (valid) validFuelUsed += p.fuelUsed
        return SessionEvent.LapCompleted(CompletedLap(p.number, timeMs, p.measuredMs, p.fuelUsed, valid, p.samples))
    }

    /** GT7 never reported the lap time: keep the measured one, but the lap cannot be trusted. */
    private fun finalizeFallback(p: PendingLap) = finalize(p.copy(invalid = true), p.measuredMs)

    private fun resetLap(frame: TelemetryFrame, observed: Boolean) {
        lapStartObserved = observed
        lapMeasuredMs = 0
        lapDistanceM = 0f
        lapStartFuel = frame.fuelLevel
        lapInvalid = false
        lapSamples = mutableListOf()
        sample(frame)
    }

    private fun sample(frame: TelemetryFrame) {
        val timeMs = lapMeasuredMs.toInt()
        val last = lapSamples.lastOrNull()
        if (last != null && timeMs - last.timeMs < SAMPLE_INTERVAL_MS) return
        lapSamples += LapSample(
            timeMs, lapDistanceM, frame.speedMps, frame.throttle, frame.brake, frame.gear, frame.rpm,
            frame.x, frame.z, frame.boostBar,
        )
    }

    private fun remember(frame: TelemetryFrame, atMs: Long) {
        lastFrameAtMs = atMs
        prevPacketId = frame.packetId
        prevLastLapMs = frame.lastLapMs
    }

    private fun publish(frame: TelemetryFrame) {
        val w = frame.wheelRps
        val r = frame.tireRadiusM
        val delta = if (active && lapStartObserved) liveDeltaMs(lapMeasuredMs.toInt(), lapDistanceM, reference) else null
        val fuelLaps = if (active) fuelLapsRemaining(frame.fuelLevel, validFuelUsed) else null
        _live.value = LiveState(
            frame = frame,
            sessionActive = active,
            lapTimeMs = if (active) lapMeasuredMs.toInt() else 0,
            distanceM = if (active) lapDistanceM else 0f,
            deltaMs = delta,
            predictedLapMs = referenceMs?.let { ref -> delta?.let { ref + it } },
            fuelLapsRemaining = fuelLaps,
            fuelMarginLaps = if (active) {
                fuelMarginLaps(fuelLaps, frame.currentLap, frame.totalLaps, lapDistanceM, reference?.lastOrNull()?.distanceM)
            } else null,
            slip = listOf(
                wheelSlip(w.fl, r.fl, frame.speedMps),
                wheelSlip(w.fr, r.fr, frame.speedMps),
                wheelSlip(w.rl, r.rl, frame.speedMps),
                wheelSlip(w.rr, r.rr, frame.speedMps),
            ),
        )
    }

    private data class PendingLap(
        val number: Int,
        val measuredMs: Int,
        val fuelUsed: Float,
        val invalid: Boolean,
        val samples: List<LapSample>,
        val lastLapMsBefore: Int,
        val incrementAtMs: Long,
    )

    private companion object {
        val IDLE = LiveState(null, false, 0, 0f, null, null, null, null, listOf(null, null, null, null))
    }
}
