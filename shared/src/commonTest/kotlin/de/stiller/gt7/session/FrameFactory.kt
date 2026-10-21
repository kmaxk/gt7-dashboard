package de.stiller.gt7.session

import de.stiller.gt7.telemetry.TelemetryFrame
import de.stiller.gt7.telemetry.Wheels

private val ZERO = Wheels(0f, 0f, 0f, 0f)

fun frame(
    packetId: Int = 0,
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
    speedMps: Float = 0f,
    rpm: Float = 0f,
    rpmAlertMin: Int = 0,
    rpmAlertMax: Int = 0,
    gear: Int = 0,
    suggestedGear: Int = 0,
    throttle: Float = 0f,
    brake: Float = 0f,
    boostBar: Float = 0f,
    fuelLevel: Float = 0f,
    fuelCapacity: Float = 0f,
    tireTempC: Wheels = ZERO,
    wheelRps: Wheels = ZERO,
    tireRadiusM: Wheels = Wheels(0.33f, 0.33f, 0.33f, 0.33f),
    currentLap: Int = 0,
    totalLaps: Int = 0,
    bestLapMs: Int = 0,
    lastLapMs: Int = 0,
    racePosition: Int = 0,
    totalPositions: Int = 0,
    carCode: Int = 0,
    onTrack: Boolean = true,
    paused: Boolean = false,
    loading: Boolean = false,
    hasTurbo: Boolean = false,
) = TelemetryFrame(
    packetId, x, y, z, speedMps, rpm, rpmAlertMin, rpmAlertMax, gear, suggestedGear, throttle, brake, boostBar,
    fuelLevel, fuelCapacity, tireTempC, wheelRps, tireRadiusM, currentLap, totalLaps, bestLapMs, lastLapMs,
    racePosition, totalPositions, carCode, onTrack, paused, loading, hasTurbo,
)
