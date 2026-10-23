package de.bavaco.gt7.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineerTest {
    /** A corner of a synthetic lap: braking over [brakeLen] m down to [apex] m/s at [at], held for [throttleDelay] m, then back to 60 m/s over 150 m. */
    private data class C(
        val at: Float,
        val brakeLen: Float = 100f,
        val apex: Float = 20f,
        val throttleDelay: Float = 10f,
        val gear: Int = 3,
        val brakes: Boolean = true,
    )

    private val straight = 60f

    private fun speedAt(d: Float, corners: List<C>): Float {
        for (c in corners) {
            val accelFrom = c.at + c.throttleDelay
            when {
                d >= c.at - c.brakeLen && d < c.at -> return straight + (c.apex - straight) * ((d - (c.at - c.brakeLen)) / c.brakeLen)
                d >= c.at && d < accelFrom -> return c.apex
                d >= accelFrom && d < accelFrom + 150f -> return c.apex + (straight - c.apex) * ((d - accelFrom) / 150f)
            }
        }
        return straight
    }

    /** Samples every 5 m over [length] m; time integrates the speed profile. */
    private fun lap(corners: List<C>, length: Float = 1500f): List<LapSample> {
        val out = ArrayList<LapSample>()
        var t = 0.0
        var d = 0f
        var prev = speedAt(0f, corners)
        while (d <= length) {
            val v = speedAt(d, corners)
            if (d > 0f) t += 5.0 / ((v + prev) / 2.0)
            prev = v
            val c = corners.firstOrNull { d >= it.at - it.brakeLen && d < it.at + it.throttleDelay }
            val brake = if (c != null && c.brakes && d < c.at) 1f else 0f
            val throttle = if (c == null) 1f else 0f
            val gear = corners.firstOrNull { d >= it.at - 30f && d <= it.at + it.throttleDelay + 30f }?.gear ?: 5
            out += LapSample((t * 1000).toInt(), d, v, throttle, brake, gear, 0f, d, 0f, 0f)
            d += 5f
        }
        return out
    }

    private val base = listOf(C(300f), C(800f), C(1300f))
    private fun with(i: Int, c: C) = base.toMutableList().also { it[i] = c }

    @Test
    fun cornersComeFromTheReferenceInDrivingOrder() {
        val d = Engineer.analyze(lap(base), lap(base))
        assertEquals(listOf(1, 2, 3), d.reports.map { it.corner.number })
        assertEquals(listOf(300f, 800f, 1300f), d.reports.map { it.corner.apexM })
        assertEquals(0f, d.reports[0].corner.startM)
        assertEquals(1500f, d.reports[2].corner.endM)
        // a zone ends where the speed is back at its peak, which starts the next zone
        assertEquals(d.reports[0].corner.endM, d.reports[1].corner.startM)
        assertTrue(d.reports[0].corner.endM in 450f..500f, "${d.reports[0].corner.endM}")
    }

    @Test
    fun identicalLapsGiveNoTips() {
        val d = Engineer.analyze(lap(base), lap(base))
        assertTrue(d.tips.isEmpty())
        assertEquals(0, d.totalLossMs)
        assertTrue(d.reports.all { it.causes.isEmpty() && it.lossMs == 0 })
    }

    @Test
    fun brakingEarlierIsTheMainCause() {
        val d = Engineer.analyze(lap(with(1, C(800f, brakeLen = 115f))), lap(base))
        val tip = d.tips.single()
        assertEquals(2, tip.corner.number)
        assertEquals(Cause.BrakeEarly(15), tip.causes.first())
        assertTrue(tip.lossMs > 50, "${tip.lossMs}")
        assertEquals(685f, tip.brakeM)
        assertEquals(700f, tip.refBrakeM)
    }

    @Test
    fun slowerApexIsReportedInKmh() {
        val d = Engineer.analyze(lap(with(0, C(300f, apex = 20f - 6f / 3.6f))), lap(base))
        val tip = d.tips.single()
        assertEquals(Cause.SlowApex(6), tip.causes.first())
        assertEquals(66f, tip.apexKmh, 0.5f)
        assertEquals(72f, tip.refApexKmh, 0.5f)
    }

    @Test
    fun lateThrottleIsMeasuredInMeters() {
        val d = Engineer.analyze(lap(with(2, C(1300f, throttleDelay = 30f))), lap(base))
        assertEquals(Cause.LateThrottle(20), d.tips.single().causes.first())
    }

    @Test
    fun fasterApexThatStillLosesIsOverspeed() {
        val d = Engineer.analyze(lap(with(0, C(300f, apex = 23f, throttleDelay = 60f))), lap(base))
        // the late exit also spills into the next zone, which starts where the comparison is back at full speed
        val tip = d.tips.first { it.corner.number == 1 }
        assertEquals(Cause.OverspeedApex(11), tip.causes.first())
        assertTrue(Cause.LateThrottle(50) in tip.causes, "${tip.causes}")
    }

    @Test
    fun otherGearComesAfterTheMainCause() {
        val d = Engineer.analyze(lap(with(1, C(800f, brakeLen = 115f, gear = 2))), lap(base))
        val causes = d.tips.single().causes
        assertEquals(Cause.BrakeEarly(15), causes.first())
        assertEquals(Cause.GearDiffers(2, 3), causes.last())
    }

    @Test
    fun smallDifferencesStayBelowTheThresholds() {
        val d = Engineer.analyze(lap(with(1, C(800f, brakeLen = 104f))), lap(base))
        assertTrue(d.tips.isEmpty(), "${d.tips}")
        // the report is still there, without a cause
        assertTrue(d.reports[1].lossMs in 1..50, "${d.reports[1].lossMs}")
        assertTrue(d.reports[1].causes.isEmpty())
    }

    @Test
    fun atMostFiveTipsSortedByLoss() {
        val ref = (1..7).map { C(it * 400f) }
        val slow = ref.mapIndexed { i, c -> c.copy(throttleDelay = 10f + 10f * (i + 1)) }
        val d = Engineer.analyze(lap(slow, 3000f), lap(ref, 3000f))
        assertEquals(7, d.reports.size)
        assertEquals(listOf(7, 6, 5, 4, 3), d.tips.map { it.corner.number })
        assertEquals(d.tips.map { it.lossMs }.sortedDescending(), d.tips.map { it.lossMs })
    }

    @Test
    fun fasterEverywhereGivesNoTips() {
        val d = Engineer.analyze(lap(base.map { it.copy(brakeLen = 85f) }), lap(base))
        assertTrue(d.tips.isEmpty())
        assertTrue(d.totalLossMs < 0, "${d.totalLossMs}")
    }

    @Test
    fun cornersWithoutLossHaveNoCause() {
        // brakes 15 m later and gains time: nothing to fix
        val d = Engineer.analyze(lap(with(1, C(800f, brakeLen = 85f, gear = 2))), lap(base))
        assertTrue(d.reports[1].lossMs < 0, "${d.reports[1].lossMs}")
        assertTrue(d.reports[1].causes.isEmpty(), "${d.reports[1].causes}")
    }

    @Test
    fun noBrakingMeansNoBrakeValueAndNoBrakeCause() {
        val d = Engineer.analyze(lap(with(0, C(300f, brakes = false))), lap(base))
        assertNull(d.reports[0].brakeM)
        assertEquals(200f, d.reports[0].refBrakeM)
        assertTrue(d.reports[0].causes.none { it is Cause.BrakeEarly || it is Cause.BrakeLate })
    }

    @Test
    fun cornersBeyondTheShorterLapAreDropped() {
        val d = Engineer.analyze(lap(base, 1000f), lap(base))
        assertEquals(listOf(1, 2), d.reports.map { it.corner.number })
        assertTrue(d.reports.last().corner.endM <= 1000f)
    }

    @Test
    fun tooShortLapsGiveAnEmptyDebrief() {
        assertTrue(Engineer.analyze(lap(base).take(1), lap(base)).reports.isEmpty())
        assertTrue(Engineer.analyze(lap(base), emptyList()).reports.isEmpty())
    }

    private fun dyn(
        releaseM: Float? = null, releaseMs: Int? = null, steerAtThrottlePct: Int? = null, steerPerYaw: Float? = null,
        countersteerMs: Int? = null, yawPerSteerExit: Float? = null, apexLatG: Float? = null, minFrontSlip: Float? = null, maxRearSlip: Float? = null,
    ) = CornerDynamics(releaseM, releaseMs, null, steerAtThrottlePct, steerPerYaw, countersteerMs, yawPerSteerExit, apexLatG, minFrontSlip, maxRearSlip)

    private fun m(dyn: CornerDynamics, brakeM: Float? = 100f, apexKmh: Float = 80f, throttleM: Float? = 250f) =
        Engineer.Measures(brakeM, apexKmh, throttleM, 3, dyn)

    @Test
    fun releaseEarlyAndAbrupt() {
        val causes = Engineer.causes(m(dyn(releaseM = 135f, releaseMs = 100), brakeM = 80f), m(dyn(releaseM = 150f, releaseMs = 400)), 200)
        assertEquals(listOf(Cause.BrakeEarly(20), Cause.ReleaseEarly(15), Cause.ReleaseAbrupt(100, 400)), causes)
    }

    @Test
    fun releaseLateOnlyWhenTheApexIsSlower() {
        val late = dyn(releaseM = 165f)
        assertEquals(listOf<Cause>(), Engineer.causes(m(late), m(dyn(releaseM = 150f)), 200))
        assertEquals(listOf(Cause.ReleaseLate(15), Cause.SlowApex(5)), Engineer.causes(m(late, apexKmh = 75f), m(dyn(releaseM = 150f)), 200))
    }

    @Test
    fun noNewCausesWithoutLoss() {
        val bad = dyn(releaseM = 100f, releaseMs = 50, minFrontSlip = -0.3f, maxRearSlip = 0.4f, countersteerMs = 500)
        assertEquals(emptyList(), Engineer.causes(m(bad), m(dyn(releaseM = 150f, releaseMs = 400)), 0))
    }

    @Test
    fun throttleLateReplacesLateThrottle() {
        val causes = Engineer.causes(m(dyn(steerAtThrottlePct = 20), throttleM = 270f), m(dyn(steerAtThrottlePct = 50)), 200)
        assertEquals(listOf(Cause.ThrottleLate(20, 50)), causes)
    }

    @Test
    fun throttleOnLock() {
        assertEquals(listOf(Cause.ThrottleOnLock(70, 40)), Engineer.causes(m(dyn(steerAtThrottlePct = 70)), m(dyn(steerAtThrottlePct = 40)), 200))
    }

    @Test
    fun understeerAndOversteer() {
        assertEquals(listOf(Cause.Understeer), Engineer.causes(m(dyn(steerPerYaw = 1.4f)), m(dyn(steerPerYaw = 1f)), 200))
        assertEquals(listOf(Cause.Oversteer(true)), Engineer.causes(m(dyn(countersteerMs = 300)), m(dyn(countersteerMs = 0)), 200))
        assertEquals(listOf(Cause.Oversteer(false)), Engineer.causes(m(dyn(yawPerSteerExit = 1.3f)), m(dyn(yawPerSteerExit = 1f)), 200))
    }

    @Test
    fun referenceWithoutDynamics() {
        val own = dyn(releaseM = 135f, minFrontSlip = -0.2f, countersteerMs = 300, steerPerYaw = 2f, apexLatG = 1f, steerAtThrottlePct = 90)
        val ref = dyn(releaseM = 150f)
        assertEquals(listOf(Cause.ReleaseEarly(15), Cause.LockUp, Cause.Oversteer(true)), Engineer.causes(m(own), m(ref), 200))
    }

    @Test
    fun causeOrderFollowsCornerPhase() {
        val own = dyn(releaseMs = 100, apexLatG = 1.3f, maxRearSlip = 0.3f)
        val ref = dyn(releaseMs = 400, apexLatG = 1.5f)
        assertEquals(
            listOf(Cause.BrakeEarly(20), Cause.ReleaseAbrupt(100, 400), Cause.GripUnused(1.3f, 1.5f), Cause.Wheelspin),
            Engineer.causes(m(own, brakeM = 80f), m(ref), 200),
        )
    }

    @Test
    fun reportsCarryDynamicsOfBothLaps() {
        val d = Engineer.analyze(lap(with(1, C(800f, brakeLen = 140f))), lap(base))
        val r = d.reports[1]
        assertTrue(r.dynamics.releaseM != null && r.refDynamics.releaseM != null)
    }
}
