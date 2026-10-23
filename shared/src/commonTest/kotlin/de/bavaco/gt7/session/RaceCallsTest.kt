package de.bavaco.gt7.session

import de.bavaco.gt7.telemetry.Wheels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RaceCallsTest {
    private fun live(totalLaps: Int = 0, currentLap: Int = 0, fuelMargin: Float? = null) =
        LiveState(frame(totalLaps = totalLaps, currentLap = currentLap), true, 0, 0f, null, null, null, fuelMargin, listOf(null, null, null, null))

    private fun lap(number: Int, timeMs: Int, valid: Boolean = true) = CompletedLap(number, timeMs, timeMs, 1f, valid, emptyList())

    private fun temps(front: Float, rear: Float, speed: Float = 40f) =
        frame(speedMps = speed, tireTempC = Wheels(front, front, rear, rear))

    @Test
    fun lapDoneInRace() {
        val call = RaceCalls().lapDone(lap(8, 117_000), 117_300, live(totalLaps = 11, currentLap = 9, fuelMargin = -0.4f))
        assertEquals(RaceCall.LapDone(117_000, true, -300, 3, 0.4f), call)
    }

    @Test
    fun lastLapAndFinish() {
        assertEquals(1, RaceCalls().lapDone(lap(10, 117_000), null, live(totalLaps = 11, currentLap = 11)).lapsLeft)
        assertNull(RaceCalls().lapDone(lap(11, 117_000), null, live(totalLaps = 11, currentLap = 12)).lapsLeft)
    }

    @Test
    fun outsideRacesNoLapsLeftAndEnoughFuelIsNotCalled() {
        val call = RaceCalls().lapDone(lap(3, 90_000), 91_000, live(fuelMargin = 2f))
        assertEquals(RaceCall.LapDone(90_000, true, -1_000, null, null), call)
    }

    @Test
    fun invalidLapHasNoDelta() {
        assertNull(RaceCalls().lapDone(lap(3, 90_000, valid = false), 91_000, live()).deltaMs)
    }

    @Test
    fun coldTyresAreCalledOnceWhenGettingGoing() {
        val c = RaceCalls()
        assertNull(c.onFrame(temps(40f, 40f, speed = 5f), 0))
        assertEquals(RaceCall.TiresCold, c.onFrame(temps(40f, 40f), 16))
        assertNull(c.onFrame(temps(40f, 40f), 32))
    }

    @Test
    fun warmTyresAtStartAreNotCalled() {
        assertNull(RaceCalls().onFrame(temps(70f, 40f), 0))
    }

    @Test
    fun hotAxleIsCalledAfterHoldingAndOnlyOnce() {
        val c = RaceCalls()
        assertNull(c.onFrame(temps(80f, 80f), 0))
        assertNull(c.onFrame(temps(115f, 80f), 1_000))
        assertNull(c.onFrame(temps(115f, 80f), 3_900))
        assertEquals(RaceCall.TiresHot(Axle.Front), c.onFrame(temps(115f, 80f), 4_000))
        assertNull(c.onFrame(temps(115f, 80f), 9_000))
        // warm is not cool enough to call again
        c.onFrame(temps(100f, 80f), 10_000)
        c.onFrame(temps(115f, 80f), 11_000)
        assertNull(c.onFrame(temps(115f, 80f), 15_000))
        // back to normal re-arms
        c.onFrame(temps(80f, 80f), 16_000)
        c.onFrame(temps(115f, 80f), 17_000)
        assertEquals(RaceCall.TiresHot(Axle.Front), c.onFrame(temps(115f, 80f), 20_000))
    }

    @Test
    fun shortSpikeIsNotCalled() {
        val c = RaceCalls()
        c.onFrame(temps(80f, 115f), 0)
        c.onFrame(temps(80f, 100f), 2_000)
        c.onFrame(temps(80f, 115f), 2_500)
        assertNull(c.onFrame(temps(80f, 115f), 4_000))
    }

    @Test
    fun bothAxlesTogether() {
        val c = RaceCalls()
        c.onFrame(temps(115f, 115f), 0)
        assertEquals(RaceCall.TiresHot(Axle.Both), c.onFrame(temps(115f, 115f), 3_000))
    }

    @Test
    fun resetStartsOver() {
        val c = RaceCalls()
        c.onFrame(temps(40f, 40f), 0)
        c.reset()
        assertEquals(RaceCall.TiresCold, c.onFrame(temps(40f, 40f), 16))
    }
}
