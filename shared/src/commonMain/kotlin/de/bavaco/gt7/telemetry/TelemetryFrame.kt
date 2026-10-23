package de.bavaco.gt7.telemetry

/** Per-wheel values in the order FL, FR, RL, RR. */
data class Wheels(val fl: Float, val fr: Float, val rl: Float, val rr: Float)

/** Dashboard lamps from the packet flags; [asm] and [tcs] are on while the aid intervenes. */
data class CarFlags(
    val lights: Boolean = false,
    val highBeam: Boolean = false,
    val asm: Boolean = false,
    val tcs: Boolean = false,
    val revLimiter: Boolean = false,
    val handBrake: Boolean = false,
)

data class TelemetryFrame(
    val packetId: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val speedMps: Float,
    val rpm: Float,
    val rpmAlertMin: Int,
    val rpmAlertMax: Int,
    val gear: Int,
    val suggestedGear: Int,
    val throttle: Float,
    val brake: Float,
    val boostBar: Float,
    val fuelLevel: Float,
    val fuelCapacity: Float,
    val tireTempC: Wheels,
    val wheelRps: Wheels,
    val tireRadiusM: Wheels,
    val currentLap: Int,
    val totalLaps: Int,
    val bestLapMs: Int,
    val lastLapMs: Int,
    val racePosition: Int,
    val totalPositions: Int,
    val carCode: Int,
    val onTrack: Boolean,
    val paused: Boolean,
    val loading: Boolean,
    val hasTurbo: Boolean,
    val oilPressureBar: Float = 0f,
    val waterTempC: Float = 0f,
    val oilTempC: Float = 0f,
    val carFlags: CarFlags = CarFlags(),
    /** Steering wheel angle in rad; packet type B only. */
    val steeringRad: Float? = null,
    /** Rotation about the vertical axis in rad/s. */
    val yawRate: Float? = null,
    /** Body accelerations in m/s² (sway, surge); packet type B only. */
    val latAccel: Float? = null,
    val longAccel: Float? = null,
)
