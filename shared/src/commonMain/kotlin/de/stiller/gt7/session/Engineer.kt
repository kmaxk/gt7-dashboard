package de.stiller.gt7.session

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
    /** Main cause first: braking, apex, throttle, gear; empty unless the corner loses time. */
    val causes: List<Cause>,
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
                causes(own, ref, loss),
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

    private class Measures(val brakeM: Float?, val apexKmh: Float, val throttleM: Float?, val gear: Int)

    private fun measure(samples: List<LapSample>, c: Corner): Measures? {
        val zone = samples.filter { it.distanceM >= c.startM && it.distanceM <= c.endM }
        val apex = zone.minByOrNull { it.speedMps } ?: return null
        val brake = zone.firstOrNull { it.distanceM <= apex.distanceM && it.brake > BRAKE_ON }
        val throttle = zone.firstOrNull { it.distanceM > apex.distanceM && it.throttle > THROTTLE_ON }
        return Measures(brake?.distanceM, apex.speedMps * 3.6f, throttle?.distanceM, apex.gear)
    }

    /** Causes only explain lost time: a corner that is as fast or faster has none. */
    private fun causes(own: Measures, ref: Measures, lossMs: Int): List<Cause> {
        if (lossMs <= 0) return emptyList()
        val out = ArrayList<Cause>()
        if (own.brakeM != null && ref.brakeM != null) {
            val diff = own.brakeM - ref.brakeM
            if (diff < -BRAKE_TOLERANCE_M) out += Cause.BrakeEarly(abs(diff).roundToInt())
            if (diff > BRAKE_TOLERANCE_M) out += Cause.BrakeLate(diff.roundToInt())
        }
        val apexDiff = own.apexKmh - ref.apexKmh
        if (apexDiff < -APEX_TOLERANCE_KMH) out += Cause.SlowApex(abs(apexDiff).roundToInt())
        if (apexDiff > APEX_TOLERANCE_KMH) out += Cause.OverspeedApex(apexDiff.roundToInt())
        if (own.throttleM != null && ref.throttleM != null && own.throttleM - ref.throttleM > THROTTLE_TOLERANCE_M) {
            out += Cause.LateThrottle((own.throttleM - ref.throttleM).roundToInt())
        }
        if (own.gear != ref.gear) out += Cause.GearDiffers(own.gear, ref.gear)
        return out
    }
}
