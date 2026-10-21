package de.stiller.gt7.session

import de.stiller.gt7.telemetry.TelemetryFrame

data class LapSample(
    val timeMs: Int,
    val distanceM: Float,
    val speedMps: Float,
    val throttle: Float,
    val brake: Float,
    val gear: Int,
    val rpm: Float,
    val x: Float,
    val z: Float,
    val boostBar: Float,
)

data class CompletedLap(
    val number: Int,
    val timeMs: Int,
    val measuredMs: Int,
    val fuelUsed: Float,
    val isValid: Boolean,
    val samples: List<LapSample>,
)

sealed interface SessionEvent {
    data class Started(val carCode: Int, val atMs: Long) : SessionEvent
    data class LapCompleted(val lap: CompletedLap) : SessionEvent
    data class Ended(val atMs: Long) : SessionEvent
}

/** Live view of the running session; [slip] in wheel order FL, FR, RL, RR. */
data class LiveState(
    val frame: TelemetryFrame?,
    val sessionActive: Boolean,
    val lapTimeMs: Int,
    val distanceM: Float,
    val deltaMs: Int?,
    /** Reference lap time plus [deltaMs]; null without a reference. */
    val predictedLapMs: Int?,
    val fuelLapsRemaining: Float?,
    /** Fuel laps minus race laps left; null outside races. */
    val fuelMarginLaps: Float?,
    val slip: List<Float?>,
)
