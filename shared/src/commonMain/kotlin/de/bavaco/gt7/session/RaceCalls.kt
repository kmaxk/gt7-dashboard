package de.bavaco.gt7.session

import de.bavaco.gt7.telemetry.TelemetryFrame

private const val HOT_HOLD_MS = 3_000L
private const val COLD_CHECK_MPS = 15f

/** Which tyres a call is about. */
enum class Axle { Front, Rear, Both }

/** Something the race engineer says outside the corners. */
sealed interface RaceCall {
    /**
     * At the line: the finished lap, its gap to the reference it was driven against, the laps still to go
     * including the one just begun (null outside races or once the race is over) and the fuel missing to the
     * finish in laps (null if the fuel suffices or is unknown).
     */
    data class LapDone(val timeMs: Int, val valid: Boolean, val deltaMs: Int?, val lapsLeft: Int?, val fuelShortLaps: Float?) : RaceCall
    data class TiresHot(val axle: Axle) : RaceCall
    data object TiresCold : RaceCall
}

/** Decides when the race engineer speaks up about the lap, the fuel and the tyres; one instance per session. */
class RaceCalls {
    private var coldChecked = false
    private val hotSince = arrayOfNulls<Long>(2)
    private val hotCalled = BooleanArray(2)

    fun reset() {
        coldChecked = false
        hotSince.fill(null)
        hotCalled.fill(false)
    }

    /** [live] is the state right after the lap ended: its frame already counts the next lap. */
    fun lapDone(lap: CompletedLap, referenceMs: Int?, live: LiveState): RaceCall.LapDone {
        val total = live.frame?.totalLaps ?: 0
        return RaceCall.LapDone(
            timeMs = lap.timeMs,
            valid = lap.isValid,
            deltaMs = if (lap.isValid) referenceMs?.let { lap.timeMs - it } else null,
            lapsLeft = if (total > 0) (total - lap.number).takeIf { it > 0 } else null,
            fuelShortLaps = live.fuelMarginLaps?.takeIf { it < 0f }?.let { -it },
        )
    }

    /** Tyre calls: cold once when the car first gets going, hot when an axle stays hot for [HOT_HOLD_MS]. */
    fun onFrame(frame: TelemetryFrame, atMs: Long): RaceCall? {
        val t = frame.tireTempC
        if (!coldChecked && frame.speedMps >= COLD_CHECK_MPS) {
            coldChecked = true
            if (listOf(t.fl, t.fr, t.rl, t.rr).all { tireState(it) == TireState.Cold }) return RaceCall.TiresCold
        }
        val fresh = listOf((t.fl + t.fr) / 2, (t.rl + t.rr) / 2).mapIndexed { axle, temp -> newlyHot(axle, temp, atMs) }
        return when {
            fresh[0] && fresh[1] -> RaceCall.TiresHot(Axle.Both)
            fresh[0] -> RaceCall.TiresHot(Axle.Front)
            fresh[1] -> RaceCall.TiresHot(Axle.Rear)
            else -> null
        }
    }

    /** True once per hot phase; the axle is armed again after cooling back to normal. */
    private fun newlyHot(axle: Int, tempC: Float, atMs: Long): Boolean {
        when (tireState(tempC)) {
            TireState.Hot -> {
                val since = hotSince[axle] ?: atMs.also { hotSince[axle] = it }
                if (!hotCalled[axle] && atMs - since >= HOT_HOLD_MS) {
                    hotCalled[axle] = true
                    return true
                }
            }
            TireState.Warm -> hotSince[axle] = null
            else -> {
                hotSince[axle] = null
                hotCalled[axle] = false
            }
        }
        return false
    }
}
