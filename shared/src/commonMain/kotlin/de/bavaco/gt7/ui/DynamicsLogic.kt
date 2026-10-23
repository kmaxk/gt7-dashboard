package de.bavaco.gt7.ui

import de.bavaco.gt7.session.Cause
import de.bavaco.gt7.session.CornerReport
import de.bavaco.gt7.session.LapSample

/** Whether the lap was recorded with packet type B (steering and accelerations). */
fun hasDynamics(samples: List<LapSample>): Boolean = samples.any { it.steeringRad != null }

/** `Lösen −12 m · Auslaufen 0,1 / 0,4 s · Gas bei 70 / 40 % Lenkung`: lap against comparison, `–` where a value is missing. */
fun dynamicsDetail(r: CornerReport): String {
    val d = r.dynamics
    val ref = r.refDynamics
    fun seconds(ms: Int?) = ms?.let { formatDecimal(it / 1000f, 1).replace('.', ',') } ?: "–"
    fun pct(p: Int?) = p?.toString() ?: "–"
    return "Lösen ${meterDiff(d.releaseM, ref.releaseM)} · Auslaufen ${seconds(d.releaseMs)} / ${seconds(ref.releaseMs)} s · " +
        "Gas bei ${pct(d.steerAtThrottlePct)} / ${pct(ref.steerAtThrottlePct)} % Lenkung"
}

/** Short mark for the corner row: `US` understeer, `ÜS` oversteer, null if neither. */
fun handlingMark(r: CornerReport): String? {
    val under = Cause.Understeer in r.causes
    val over = r.causes.any { it is Cause.Oversteer }
    return when {
        under && over -> "US/ÜS"
        under -> "US"
        over -> "ÜS"
        else -> null
    }
}
