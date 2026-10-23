package de.bavaco.gt7.session

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicsTest {
    /** What the driver does at a distance; null dynamics = a lap without packet type B. */
    private data class At(
        val throttle: Float = 0f,
        val brake: Float = 0f,
        val steer: Float? = null,
        val yaw: Float? = null,
        val lat: Float? = null,
        val slipFront: Float? = null,
        val slipRear: Float? = null,
    )

    private val corner = Corner(1, 0f, 200f, 400f)

    /** Samples every 10 m from 0 to 400 m; slowest at the apex (200 m), 100 ms apart. */
    private fun zone(at: (Float) -> At): List<LapSample> = (0..40).map { i ->
        val d = i * 10f
        val a = at(d)
        LapSample(
            i * 100, d, 20f + abs(d - 200f) * 0.1f, a.throttle, a.brake, 3, 6000f, 0f, 0f, 0f,
            steeringRad = a.steer, yawRate = a.yaw, latAccel = a.lat, slipFront = a.slipFront, slipRear = a.slipRear,
        )
    }

    /** Full brake until [releaseFrom], then down to zero over [releaseSamples] samples. */
    private fun brake(d: Float, releaseFrom: Float, releaseSamples: Int): Float = when {
        d < 50f -> 0f
        d <= releaseFrom -> 1f
        else -> (1f - (d - releaseFrom) / (releaseSamples * 10f)).coerceAtLeast(0f)
    }

    @Test
    fun releasePointAndReleaseTime() {
        val m = Dynamics.measure(zone { d -> At(brake = brake(d, 120f, 4)) }, corner)
        assertEquals(160f, m.releaseM)
        assertEquals(400, m.releaseMs)
    }

    @Test
    fun withoutDynamicsOnlyBrakeMetrics() {
        val m = Dynamics.measure(zone { d -> At(brake = brake(d, 120f, 4), throttle = if (d > 250f) 1f else 0f) }, corner)
        assertNotNull(m.releaseM)
        assertNotNull(m.releaseMs)
        assertNotNull(m.trailM)
        assertNull(m.steerAtThrottlePct)
        assertNull(m.steerPerYaw)
        assertNull(m.countersteerMs)
        assertNull(m.yawPerSteerExit)
        assertNull(m.apexLatG)
        assertNull(m.minFrontSlip)
        assertNull(m.maxRearSlip)
    }

    @Test
    fun trailBrakingAfterTurnIn() {
        // steering from 170 m, brake until 180 m and gone at 190 m
        val m = Dynamics.measure(zone { d -> At(brake = brake(d, 180f, 1), steer = if (d >= 170f && d <= 330f) 1f else 0f) }, corner)
        assertEquals(20f, m.trailM)
    }

    @Test
    fun steeringAtThrottle() {
        val m = Dynamics.measure(zone { d ->
            At(steer = if (d in 150f..250f) 1f else if (d in 260f..300f) 0.7f else 0f, throttle = if (d >= 260f) 1f else 0f)
        }, corner)
        assertEquals(70, m.steerAtThrottlePct)
    }

    @Test
    fun countersteerDetected() {
        val with = Dynamics.measure(zone { d -> At(steer = if (d in 250f..270f) -0.2f else 0.5f, yaw = 0.5f) }, corner)
        assertTrue(assertNotNull(with.countersteerMs) >= 200)
        val without = Dynamics.measure(zone { At(steer = 0.5f, yaw = 0.5f) }, corner)
        assertEquals(0, without.countersteerMs)
    }

    @Test
    fun yawNearZeroIgnored() {
        val m = Dynamics.measure(zone { At(steer = 0.5f, yaw = 0.01f) }, corner)
        assertNull(m.steerPerYaw)
        assertNull(m.yawPerSteerExit)
    }

    @Test
    fun steerPerYawAndYawPerSteer() {
        val m = Dynamics.measure(zone { d -> At(steer = if (d in 100f..300f) 0.5f else 0f, yaw = 0.25f) }, corner)
        assertEquals(2f, assertNotNull(m.steerPerYaw), 0.001f)
        assertEquals(0.5f, assertNotNull(m.yawPerSteerExit), 0.001f)
    }

    @Test
    fun lockUpAndWheelspin() {
        val m = Dynamics.measure(zone { d ->
            At(
                brake = brake(d, 120f, 4), throttle = if (d > 250f) 0.9f else 0f,
                slipFront = if (d == 100f) -0.2f else 0f, slipRear = if (d == 300f) 0.25f else 0.02f,
            )
        }, corner)
        assertEquals(-0.2f, m.minFrontSlip)
        assertEquals(0.25f, m.maxRearSlip)
    }

    @Test
    fun apexLateralG() {
        val m = Dynamics.measure(zone { d -> At(lat = if (d == 200f) 14.715f else 5f) }, corner)
        assertEquals(1.5f, assertNotNull(m.apexLatG), 0.001f)
    }

    @Test
    fun noBrakingNoBrakeMetrics() {
        val m = Dynamics.measure(zone { At() }, corner)
        assertNull(m.releaseM)
        assertNull(m.releaseMs)
        assertNull(m.trailM)
    }
}
