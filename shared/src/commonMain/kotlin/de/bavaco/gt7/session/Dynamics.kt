package de.bavaco.gt7.session

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

// first guesses, partly checked against a recording of GT7 laps (see COUNTER_STEER_RAD)
private const val BRAKE_OFF = 0.05f
private const val BRAKE_HIGH = 0.8f
private const val TURN_IN = 0.15f
private const val THROTTLE_ON = 0.8f
private const val THROTTLE_DRIVE = 0.5f
private const val MIN_STEER_RAD = 0.035f // 2°
private const val MIN_YAW = 0.05f
private const val COUNTER_YAW = 0.2f
// 20° at the steering wheel: in a direction change the wheel passes zero before the car does, which reads as
// countersteering for up to 250 ms at small angles; above 20° it lasted at most 150 ms in recorded laps
private const val COUNTER_STEER_RAD = 0.35f
private const val TRAIL_WINDOW = 0.3f
private const val G = 9.81f

/**
 * How a lap took one corner; a value is null if the lap lacks the data (no braking, no packet type B).
 * Distances in m from the start line, steering in rad, slip as a fraction.
 */
data class CornerDynamics(
    /** Where the brake dropped below 5 % after its peak. */
    val releaseM: Float?,
    /** From the last moment at 80 % of the peak to [releaseM]. */
    val releaseMs: Int?,
    /** Braking distance after turn-in; without steering data in the last 30 % before the apex. */
    val trailM: Float?,
    /** Steering when the throttle passes 80 %, in % of the corner's largest steering angle. */
    val steerAtThrottlePct: Int?,
    /**
     * Mean steering per path curvature (steering × speed / yaw rate) from turn-in to apex: more means the car
     * turns less for the same lock. Per curvature, not per yaw rate, so a slower lap does not look like understeer.
     */
    val steerPerYaw: Float?,
    /** Longest stretch of steering against the rotation; 0 if none, null without steering data. */
    val countersteerMs: Int?,
    /** Mean curvature per steering (yaw rate / (steering × speed)) from apex to exit: more means the rear helps turning. */
    val yawPerSteerExit: Float?,
    val apexLatG: Float?,
    /** Lowest front slip under braking (negative = locking). */
    val minFrontSlip: Float?,
    /** Highest rear slip on the throttle. */
    val maxRearSlip: Float?,
) {
    companion object {
        val NONE = CornerDynamics(null, null, null, null, null, null, null, null, null, null)
    }
}

/** Measures braking, steering and slip of a lap in one corner zone. */
object Dynamics {
    fun measure(samples: List<LapSample>, corner: Corner): CornerDynamics {
        val zone = samples.filter { it.distanceM >= corner.startM && it.distanceM <= corner.endM }
        val apex = zone.minByOrNull { it.speedMps } ?: return CornerDynamics.NONE
        val apexIndex = zone.indexOf(apex)

        val maxSteer = zone.mapNotNull { it.steeringRad?.let(::abs) }.maxOrNull()?.takeIf { it > MIN_STEER_RAD }
        val turnIn = maxSteer?.let { m -> zone.take(apexIndex + 1).firstOrNull { s -> s.steeringRad?.let { abs(it) > TURN_IN * m } == true } }

        val peakIndex = zone.take(apexIndex + 1).indices.maxByOrNull { zone[it].brake }?.takeIf { zone[it].brake >= BRAKE_OFF }
        var releaseM: Float? = null
        var releaseMs: Int? = null
        var trailM: Float? = null
        if (peakIndex != null) {
            val peak = zone[peakIndex].brake
            val release = zone.drop(peakIndex).firstOrNull { it.brake < BRAKE_OFF }
            if (release != null) {
                releaseM = release.distanceM
                val lastHigh = zone.subList(peakIndex, zone.indexOf(release)).last { it.brake >= BRAKE_HIGH * peak }
                releaseMs = release.timeMs - lastHigh.timeMs
                val fromM = turnIn?.distanceM ?: (apex.distanceM - TRAIL_WINDOW * (apex.distanceM - corner.startM))
                trailM = (release.distanceM - fromM).coerceAtLeast(0f)
            }
        }

        val throttleOn = zone.drop(apexIndex + 1).firstOrNull { it.throttle > THROTTLE_ON }
        val steerAtThrottle = if (maxSteer != null) throttleOn?.steeringRad?.let { (abs(it) / maxSteer * 100f).roundToInt() } else null

        val entry = if (turnIn != null) zone.subList(zone.indexOf(turnIn), apexIndex + 1) else emptyList()
        val exit = zone.drop(apexIndex)
        val hasSteering = zone.any { it.steeringRad != null }

        return CornerDynamics(
            releaseM = releaseM,
            releaseMs = releaseMs,
            trailM = trailM,
            steerAtThrottlePct = steerAtThrottle,
            steerPerYaw = entry.ratios { steer, yaw, v -> steer * v / yaw },
            countersteerMs = if (hasSteering) longestCountersteer(zone) else null,
            yawPerSteerExit = exit.ratios { steer, yaw, v -> yaw / (steer * v) },
            apexLatG = apex.latAccel?.let { abs(it) / G },
            minFrontSlip = zone.filter { it.brake > BRAKE_OFF }.mapNotNull { it.slipFront }.minOrNull(),
            maxRearSlip = zone.filter { it.throttle > THROTTLE_DRIVE }.mapNotNull { it.slipRear }.maxOrNull(),
        )
    }

    /** Mean of [f](|steering|, |yaw rate|, speed) over samples turning clearly enough to divide; null if none does. */
    private fun List<LapSample>.ratios(f: (Float, Float, Float) -> Float): Float? {
        val values = mapNotNull { s ->
            val steer = s.steeringRad?.let(::abs) ?: return@mapNotNull null
            val yaw = s.yawRate?.let(::abs) ?: return@mapNotNull null
            if (steer > MIN_STEER_RAD && yaw > MIN_YAW && s.speedMps > 0f) f(steer, yaw, s.speedMps) else null
        }
        return if (values.isEmpty()) null else values.sum() / values.size
    }

    private fun longestCountersteer(zone: List<LapSample>): Int {
        var longest = 0
        var start: LapSample? = null
        for (s in zone) {
            val steer = s.steeringRad
            val yaw = s.yawRate
            val counter = steer != null && yaw != null && abs(steer) > COUNTER_STEER_RAD && abs(yaw) > COUNTER_YAW && steer.sign != yaw.sign
            if (counter) {
                val first = start ?: s.also { start = it }
                longest = maxOf(longest, s.timeMs - first.timeMs)
            } else {
                start = null
            }
        }
        return longest
    }
}
