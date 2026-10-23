package de.bavaco.gt7.session

import de.bavaco.gt7.telemetry.Wheels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionEngineTest {

    /** Feeds frames to an engine in a 16 ms rhythm; mutable fields describe the next frame. */
    private class Driver {
        val engine = SessionEngine()
        val events = mutableListOf<SessionEvent>()
        var t = 0L
        var packetId = 0
        var lap = 1
        var lastLapMs = 0
        var speedMps = 50f
        var fuel = 50f
        var paused = false
        var loading = false
        var carCode = 7
        private var lapStartT = 0L

        val completed get() = events.filterIsInstance<SessionEvent.LapCompleted>().map { it.lap }

        fun step(dtMs: Long = 16): List<SessionEvent> {
            t += dtMs
            return emit()
        }

        fun drive(ms: Long) {
            var left = ms
            while (left > 0) {
                step()
                left -= 16
            }
        }

        /** Next frame increments the lap; reports the measured lap time (+ [offsetMs]) unless [report] is false. */
        fun nextLap(report: Boolean = true, offsetMs: Int = 0): Int {
            t += 16
            val measured = (t - lapStartT).toInt()
            lap++
            if (report) lastLapMs = measured + offsetMs
            lapStartT = t
            emit()
            return measured
        }

        /** Drives [laps] (first one entered at session start), each lasting [msPerLap]; lap time reported as msPerLap + finished lap number. */
        fun drive(laps: IntRange, msPerLap: Long) {
            lap = laps.first
            emit()
            lapStartT = t
            for (n in laps) {
                if (n != laps.first) {
                    t += 16
                    lap = n
                    lastLapMs = (msPerLap + n - 1).toInt()
                    lapStartT = t
                    emit()
                }
                drive(msPerLap - 16)
            }
        }

        private fun emit(): List<SessionEvent> {
            packetId++
            val out = engine.onFrame(
                frame(
                    packetId = packetId, speedMps = speedMps, fuelLevel = fuel, currentLap = lap,
                    lastLapMs = lastLapMs, carCode = carCode, paused = paused, loading = loading,
                ),
                t,
            )
            events += out
            fuel -= 0.001f
            return out
        }
    }

    /** Session started at lap 1 with a partial lap, then lap 2 begun (start observed). */
    private fun driverInLap2(): Driver = Driver().apply {
        step()
        drive(1000)
        nextLap()
    }

    @Test
    fun startsSessionOnFirstOnTrackFrame() {
        val e = SessionEngine()
        assertEquals(emptyList(), e.onFrame(frame(onTrack = false, carCode = 3), 0))
        assertEquals(emptyList(), e.onFrame(frame(loading = true, carCode = 3), 16))
        assertFalse(e.live.value.sessionActive)
        assertEquals(listOf<SessionEvent>(SessionEvent.Started(3, 32)), e.onFrame(frame(carCode = 3), 32))
        assertTrue(e.live.value.sessionActive)
        assertEquals(emptyList(), e.onFrame(frame(packetId = 1, carCode = 3), 48))
    }

    @Test
    fun completesLapOnLapIncrement() {
        val d = Driver()
        d.drive(1..3, 4000)
        val laps = d.completed
        assertEquals(1, laps.size)
        assertEquals(2, laps[0].number)
        assertEquals(4002, laps[0].timeMs) // msPerLap + finished lap number, set at the 2→3 increment
        assertEquals(4000, laps[0].measuredMs)
        assertTrue(laps[0].isValid)
        assertTrue(laps[0].fuelUsed > 0f)
    }

    @Test
    fun partialFirstLapIsNotRecorded() {
        val d = Driver()
        d.lap = 5
        d.step()
        d.drive(2000)
        d.nextLap()
        d.drive(3000)
        d.engine.onTick(d.t + 2500)
        assertEquals(emptyList(), d.completed)
    }

    @Test
    fun lapTimeTakenFromDelayedLastLapField() {
        val d = driverInLap2()
        d.drive(4000)
        val measured = d.nextLap(report = false)
        repeat(9) { d.step() }
        assertEquals(emptyList(), d.completed)
        d.lastLapMs = measured + 50
        d.step()
        val laps = d.completed
        assertEquals(1, laps.size)
        assertEquals(measured + 50, laps[0].timeMs)
        assertTrue(laps[0].isValid)
    }

    @Test
    fun pendingLapFallsBackToMeasuredTimeAfter2s() {
        val d = driverInLap2()
        d.drive(4000)
        val measured = d.nextLap(report = false)
        d.drive(2100)
        val laps = d.completed
        assertEquals(1, laps.size)
        assertEquals(measured, laps[0].timeMs)
        assertFalse(laps[0].isValid)
    }

    @Test
    fun pauseInvalidatesLap() {
        val d = driverInLap2()
        d.drive(1000)
        d.paused = true
        repeat(10) { d.step() }
        d.paused = false
        d.drive(1000)
        d.nextLap()
        assertFalse(d.completed.single().isValid)
    }

    @Test
    fun pausedTimeIsNotLapTime() {
        val d = driverInLap2()
        d.drive(1000)
        val before = d.engine.live.value.lapTimeMs
        d.paused = true
        repeat(300) { d.step() } // GT7 keeps sending while paused
        assertEquals(before + 16, d.engine.live.value.lapTimeMs) // only the step into the pause
        d.paused = false
        d.step()
        assertEquals(before + 16, d.engine.live.value.lapTimeMs) // the step out of the pause is paused time, too
        d.step()
        assertEquals(before + 32, d.engine.live.value.lapTimeMs)
    }

    @Test
    fun packetIdBackwardsInvalidatesLap() {
        val d = driverInLap2()
        d.drive(1000)
        d.packetId -= 100
        d.drive(1000)
        d.nextLap()
        assertFalse(d.completed.single().isValid)
    }

    @Test
    fun timeMismatchInvalidatesLap() {
        val d = driverInLap2()
        d.drive(4000)
        val measured = d.nextLap(offsetMs = 3000)
        val lap = d.completed.single()
        assertEquals(measured + 3000, lap.timeMs)
        assertEquals(measured, lap.measuredMs)
        assertFalse(lap.isValid)
    }

    @Test
    fun gapDoesNotInflateDistance() {
        val d = driverInLap2()
        d.drive(500)
        val before = d.engine.live.value.distanceM
        d.step(1000)
        val after = d.engine.live.value.distanceM
        assertTrue(after - before <= 5f, "distance grew by ${after - before}")
        assertTrue(after > before)
    }

    @Test
    fun samplesAre10Hz() {
        val d = driverInLap2()
        d.drive(1000)
        d.nextLap()
        val samples = d.completed.single().samples
        assertTrue(samples.size in 9..11, "got ${samples.size} samples")
        assertEquals(0, samples.first().timeMs)
        samples.zipWithNext { a, b -> assertTrue(b.timeMs - a.timeMs >= 100) }
    }

    @Test
    fun timeoutEndsSession() {
        val d = Driver()
        d.step()
        d.drive(1000)
        val last = d.t
        assertEquals(emptyList(), d.engine.onTick(last + 29_000))
        assertEquals(listOf<SessionEvent>(SessionEvent.Ended(last + 30_001)), d.engine.onTick(last + 30_001))
        assertFalse(d.engine.live.value.sessionActive)
    }

    @Test
    fun carChangeRestartsSession() {
        val d = Driver()
        d.step()
        d.drive(500)
        d.carCode = 8
        assertEquals(emptyList(), d.step())
        d.drive(400)
        val out = d.step(100)
        assertEquals(listOf(SessionEvent.Ended(d.t), SessionEvent.Started(8, d.t)), out)
    }

    @Test
    fun lastingLoadingEndsSession() {
        val d = driverInLap2()
        d.drive(500)
        d.loading = true
        d.drive(480)
        assertTrue(d.engine.live.value.sessionActive)
        d.step(50)
        assertEquals(SessionEvent.Ended(d.t), d.events.last())
        assertFalse(d.engine.live.value.sessionActive)
    }

    @Test
    fun glitchFramesInRaceKeepSessionAndLap() {
        val d = driverInLap2()
        d.drive(2000)
        d.loading = true
        d.step()
        d.loading = false
        d.drive(2000)
        d.carCode = 8
        d.step()
        d.carCode = 7
        d.drive(2000)
        val measured = d.nextLap()
        assertTrue(d.events.none { it is SessionEvent.Ended })
        val lap = d.completed.single { it.number == 2 }
        assertTrue(lap.isValid)
        assertEquals(measured, lap.measuredMs)
    }

    @Test
    fun liveStateHasDeltaWithReference() {
        val d = driverInLap2()
        d.drive(500)
        assertNull(d.engine.live.value.deltaMs)
        d.engine.setReference(
            listOf(
                LapSample(0, 0f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f),
                LapSample(20_000, 1000f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f),
            ),
            20_000,
        )
        d.step()
        assertNotNull(d.engine.live.value.deltaMs)
    }

    @Test
    fun predictedLapIsReferenceTimePlusDelta() {
        val d = driverInLap2()
        d.drive(500)
        assertNull(d.engine.live.value.predictedLapMs)
        d.engine.setReference(
            listOf(
                LapSample(0, 0f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f),
                LapSample(100_000, 5000f, 0f, 0f, 0f, 0, 0f, 0f, 0f, 0f),
            ),
            100_000,
        )
        d.step()
        val live = d.engine.live.value
        assertEquals(100_000 + live.deltaMs!!, live.predictedLapMs)
    }

    @Test
    fun endSessionStopsAndNextFrameRestarts() {
        val d = driverInLap2()
        d.drive(500)
        val ended = d.engine.endSession(d.t)
        assertTrue(ended.any { it is SessionEvent.Ended })
        assertFalse(d.engine.live.value.sessionActive)
        assertTrue(d.step().any { it is SessionEvent.Started })
    }

    @Test
    fun endSessionFinalizesPendingLap() {
        val d = driverInLap2()
        d.drive(4000)
        d.nextLap(report = false)
        val ended = d.engine.endSession(d.t)
        val completedAt = ended.indexOfFirst { it is SessionEvent.LapCompleted }
        assertTrue(completedAt >= 0 && completedAt < ended.indexOfFirst { it is SessionEvent.Ended })
        assertFalse((ended[completedAt] as SessionEvent.LapCompleted).lap.isValid)
    }

    @Test
    fun endSessionWithoutSessionIsNoop() {
        assertTrue(SessionEngine().endSession(0).isEmpty())
    }

    /** Lap 2 driven with [speedMps] and dynamics; the rear wheels turn 20 % faster than the front ones. */
    private fun dynamicsLap(speedMps: Float): CompletedLap {
        val e = SessionEngine()
        val front = 50f / 0.33f
        fun f(id: Int, lap: Int, lastLapMs: Int = 0) = frame(
            packetId = id, speedMps = speedMps, currentLap = lap, lastLapMs = lastLapMs, carCode = 7,
            wheelRps = Wheels(front, front, front * 1.2f, front * 1.2f),
            steeringRad = 0.3f, yawRate = 0.5f, latAccel = 12f, longAccel = -3f,
        )
        var t = 0L
        var id = 0
        e.onFrame(f(++id, 1), t)
        t += 16
        e.onFrame(f(++id, 2), t)
        repeat(20) { t += 16; e.onFrame(f(++id, 2), t) }
        t += 16
        val out = e.onFrame(f(++id, 3, lastLapMs = 336), t)
        return out.filterIsInstance<SessionEvent.LapCompleted>().single().lap
    }

    @Test
    fun samplesCarryDynamics() {
        val s = dynamicsLap(50f).samples.first()
        assertEquals(0.3f, s.steeringRad)
        assertEquals(0.5f, s.yawRate)
        assertEquals(12f, s.latAccel)
        assertEquals(-3f, s.longAccel)
        assertEquals(0f, assertNotNull(s.slipFront), 0.01f)
        assertEquals(0.2f, assertNotNull(s.slipRear), 0.01f)
    }

    @Test
    fun slipNullBelowSlipSpeed() {
        val s = dynamicsLap(2f).samples.first()
        assertNull(s.slipFront)
        assertNull(s.slipRear)
    }
}
