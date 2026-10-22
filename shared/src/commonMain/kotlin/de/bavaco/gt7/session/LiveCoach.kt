package de.bavaco.gt7.session

import kotlin.math.roundToInt

/** A corner to announce: [inM] meters to its braking point (0 once past it), rounded to 10 m. */
data class CoachTip(val report: CornerReport, val inM: Int)

private const val MAX_LIVE_TIPS = 3
private const val LEAD_S = 5f
private const val MIN_LEAD_M = 120f
private const val MAX_LEAD_M = 400f
private const val ROUND_M = 10

/** Remembers the costliest corners of the last lap and names the next one shortly before it. */
class LiveCoach {
    private var corners: List<CornerReport> = emptyList()

    fun set(debrief: Debrief?) {
        corners = debrief?.tips?.filter { it.causes.isNotEmpty() }?.take(MAX_LIVE_TIPS)?.sortedBy { it.corner.startM } ?: emptyList()
    }

    fun tipAt(distanceM: Float, speedMps: Float): CoachTip? {
        val lead = (speedMps * LEAD_S).coerceIn(MIN_LEAD_M, MAX_LEAD_M)
        val hit = corners.firstOrNull { distanceM <= it.corner.apexM && distanceM >= anchor(it) - lead } ?: return null
        val ahead = (anchor(hit) - distanceM).coerceAtLeast(0f)
        return CoachTip(hit, (ahead / ROUND_M).roundToInt() * ROUND_M)
    }

    private fun anchor(r: CornerReport) = r.refBrakeM ?: r.corner.startM
}
