package de.bavaco.gt7.session

import kotlin.math.abs
import kotlin.math.roundToInt

/** A corner of the comparison lap: zone from the speed peak before the apex to the peak after it, in m from the start line. */
data class Corner(val number: Int, val startM: Float, val apexM: Float, val endM: Float)

/** Why a corner costs time; meters and km/h are absolute differences to the comparison lap. */
sealed interface Cause {
    data class BrakeEarly(val meters: Int) : Cause
    data class BrakeLate(val meters: Int) : Cause
    data class SlowApex(val kmh: Int) : Cause
    data class OverspeedApex(val kmh: Int) : Cause
    data class LateThrottle(val meters: Int) : Cause
    data class GearDiffers(val lap: Int, val reference: Int) : Cause
    data class ReleaseEarly(val meters: Int) : Cause
    data class ReleaseLate(val meters: Int) : Cause
    data class ReleaseAbrupt(val ms: Int, val refMs: Int) : Cause
    data object LockUp : Cause
    data object Understeer : Cause
    data class GripUnused(val g: Float, val refG: Float) : Cause
    /** Throttle at [pct] % of the corner's steering, the reference at [refPct] %; percent of lock, not of throttle. */
    data class ThrottleLate(val pct: Int, val refPct: Int) : Cause
    data class ThrottleOnLock(val pct: Int, val refPct: Int) : Cause
    data class Oversteer(val countersteer: Boolean) : Cause
    data object Wheelspin : Cause
}

/** One corner, lap against comparison; a measure is null if the zone has no such moment (e.g. no braking). */
data class CornerReport(
    val corner: Corner,
    /** Time lost in the zone, positive = lap slower. */
    val lossMs: Int,
    val brakeM: Float?, val refBrakeM: Float?,
    val apexKmh: Float, val refApexKmh: Float,
    val throttleM: Float?, val refThrottleM: Float?,
    val gear: Int, val refGear: Int,
    /** Main cause first, by corner phase: braking, apex, exit, gear; empty unless the corner loses time. */
    val causes: List<Cause>,
    val dynamics: CornerDynamics = CornerDynamics.NONE,
    val refDynamics: CornerDynamics = CornerDynamics.NONE,
)

/** [reports] for every corner in driving order; [tips] the costliest ones; [totalLossMs] the gap at the end of the common distance. */
data class Debrief(val reports: List<CornerReport>, val tips: List<CornerReport>, val totalLossMs: Int)

private const val BRAKE_ON = 0.1f
private const val THROTTLE_ON = 0.8f
private const val BRAKE_TOLERANCE_M = 5f
private const val APEX_TOLERANCE_KMH = 3f
private const val THROTTLE_TOLERANCE_M = 10f
private const val TIP_MIN_LOSS_MS = 50
private const val MAX_TIPS = 5
private const val RELEASE_TOLERANCE_M = 10f
private const val ABRUPT_MIN_DIFF_MS = 150
private const val LOCK_UP_SLIP = -0.15f
private const val WHEELSPIN_SLIP = 0.15f
private const val HANDLING_FACTOR = 1.25f
private const val GRIP_TOLERANCE_G = 0.1f
private const val STEER_TOLERANCE_PCT = 20
private const val COUNTERSTEER_MS = 200

/** Explains where and why a lap loses time against a comparison lap, corner by corner. */
object Engineer {
    fun analyze(lap: List<LapSample>, reference: List<LapSample>): Debrief {
        if (lap.size < 2 || reference.size < 2) return Debrief(emptyList(), emptyList(), 0)
        val fromM = maxOf(lap.first().distanceM, reference.first().distanceM)
        val toM = minOf(lap.last().distanceM, reference.last().distanceM)
        fun deltaAt(d: Float): Float {
            val at = d.coerceIn(fromM, toM)
            return (timeAtDistance(lap, at) ?: 0f) - (timeAtDistance(reference, at) ?: 0f)
        }
        val reports = corners(reference, toM).mapNotNull { corner ->
            val own = measure(lap, corner) ?: return@mapNotNull null
            val ref = measure(reference, corner) ?: return@mapNotNull null
            val loss = (deltaAt(corner.endM) - deltaAt(corner.startM)).roundToInt()
            CornerReport(
                corner, loss,
                own.brakeM, ref.brakeM, own.apexKmh, ref.apexKmh, own.throttleM, ref.throttleM, own.gear, ref.gear,
                causes(own, ref, loss), own.dynamics, ref.dynamics,
            )
        }
        val tips = reports.filter { it.lossMs > TIP_MIN_LOSS_MS }.sortedByDescending { it.lossMs }.take(MAX_TIPS)
        return Debrief(reports, tips, deltaAt(toM).roundToInt())
    }

    /** Every speed valley of [reference] up to [toM] is an apex; zones end at the neighbouring speed peaks. */
    fun corners(reference: List<LapSample>, toM: Float): List<Corner> {
        if (reference.isEmpty()) return emptyList()
        val pv = peaksAndValleys(reference)
        val lapStart = reference.first().distanceM
        return pv.valleys.filter { it.distanceM <= toM }.mapIndexed { i, v ->
            val start = pv.peaks.lastOrNull { it.distanceM < v.distanceM }?.distanceM ?: lapStart
            val end = pv.peaks.firstOrNull { it.distanceM > v.distanceM }?.distanceM?.coerceAtMost(toM) ?: toM
            Corner(i + 1, start, v.distanceM, end)
        }
    }

    internal class Measures(val brakeM: Float?, val apexKmh: Float, val throttleM: Float?, val gear: Int, val dynamics: CornerDynamics)

    private fun measure(samples: List<LapSample>, c: Corner): Measures? {
        val zone = samples.filter { it.distanceM >= c.startM && it.distanceM <= c.endM }
        val apex = zone.minByOrNull { it.speedMps } ?: return null
        val brake = zone.firstOrNull { it.distanceM <= apex.distanceM && it.brake > BRAKE_ON }
        val throttle = zone.firstOrNull { it.distanceM > apex.distanceM && it.throttle > THROTTLE_ON }
        return Measures(brake?.distanceM, apex.speedMps * 3.6f, throttle?.distanceM, apex.gear, Dynamics.measure(samples, c))
    }

    /**
     * Causes only explain lost time: a corner that is as fast or faster has none. Comparisons need both laps'
     * values; lock-up, wheelspin and countersteering are findings of the lap alone.
     */
    internal fun causes(own: Measures, ref: Measures, lossMs: Int): List<Cause> {
        if (lossMs <= 0) return emptyList()
        val d = own.dynamics
        val r = ref.dynamics
        val out = ArrayList<Cause>()
        // braking
        if (own.brakeM != null && ref.brakeM != null) {
            val diff = own.brakeM - ref.brakeM
            if (diff < -BRAKE_TOLERANCE_M) out += Cause.BrakeEarly(abs(diff).roundToInt())
            if (diff > BRAKE_TOLERANCE_M) out += Cause.BrakeLate(diff.roundToInt())
        }
        val slowerApex = own.apexKmh < ref.apexKmh
        both(d.releaseM, r.releaseM) { o, f ->
            if (o < f - RELEASE_TOLERANCE_M) out += Cause.ReleaseEarly((f - o).roundToInt())
            // releasing later only hurts when it costs apex speed
            if (o > f + RELEASE_TOLERANCE_M && slowerApex) out += Cause.ReleaseLate((o - f).roundToInt())
        }
        both(d.releaseMs, r.releaseMs) { own, ref ->
            if (own * 2 < ref && ref - own >= ABRUPT_MIN_DIFF_MS) out += Cause.ReleaseAbrupt(own, ref)
        }
        if (d.minFrontSlip != null && d.minFrontSlip < LOCK_UP_SLIP) out += Cause.LockUp
        // apex
        val apexDiff = own.apexKmh - ref.apexKmh
        if (apexDiff < -APEX_TOLERANCE_KMH) out += Cause.SlowApex(abs(apexDiff).roundToInt())
        if (apexDiff > APEX_TOLERANCE_KMH) out += Cause.OverspeedApex(apexDiff.roundToInt())
        both(d.steerPerYaw, r.steerPerYaw) { own, ref -> if (own > ref * HANDLING_FACTOR) out += Cause.Understeer }
        both(d.apexLatG, r.apexLatG) { own, ref -> if (ref - own >= GRIP_TOLERANCE_G) out += Cause.GripUnused(own, ref) }
        // exit
        val throttleLater = own.throttleM != null && ref.throttleM != null && own.throttleM > ref.throttleM
        val steerLate = d.steerAtThrottlePct != null && r.steerAtThrottlePct != null &&
            d.steerAtThrottlePct <= r.steerAtThrottlePct - STEER_TOLERANCE_PCT && throttleLater
        if (steerLate) {
            out += Cause.ThrottleLate(d.steerAtThrottlePct!!, r.steerAtThrottlePct!!)
        } else if (own.throttleM != null && ref.throttleM != null && own.throttleM - ref.throttleM > THROTTLE_TOLERANCE_M) {
            out += Cause.LateThrottle((own.throttleM - ref.throttleM).roundToInt())
        }
        both(d.steerAtThrottlePct, r.steerAtThrottlePct) { own, ref ->
            if (own >= ref + STEER_TOLERANCE_PCT) out += Cause.ThrottleOnLock(own, ref)
        }
        val countersteer = (d.countersteerMs ?: 0) >= COUNTERSTEER_MS
        var yawExcess = false
        both(d.yawPerSteerExit, r.yawPerSteerExit) { own, ref -> yawExcess = own > ref * HANDLING_FACTOR }
        if (countersteer || yawExcess) out += Cause.Oversteer(countersteer)
        if (d.maxRearSlip != null && d.maxRearSlip > WHEELSPIN_SLIP) out += Cause.Wheelspin
        if (own.gear != ref.gear) out += Cause.GearDiffers(own.gear, ref.gear)
        return out
    }

    private inline fun <T : Any> both(own: T?, ref: T?, block: (T, T) -> Unit) {
        if (own != null && ref != null) block(own, ref)
    }
}
