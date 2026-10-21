package de.bavaco.gt7.ui

import kotlin.math.ceil

/** LEDs per half of the shift-light strip; both halves fill from the outside towards the centre. */
const val SHIFT_LEDS = 7

enum class LedColor { Off, Green, Yellow, Red }

/**
 * Colours of one half of the shift-light strip, outside first. The strip starts filling one
 * GT7 alert band ([alertMin]..[alertMax]) below [alertMin] and is full at [alertMax].
 * Without alert values from GT7 everything stays off.
 */
fun shiftLights(rpm: Float, alertMin: Int, alertMax: Int, count: Int = SHIFT_LEDS): List<LedColor> {
    if (alertMax <= 0) return List(count) { LedColor.Off }
    val band = (alertMax - alertMin).takeIf { alertMin in 1 until alertMax } ?: (alertMax / 5)
    val start = alertMax - 2f * band
    val lit = ceil((rpm - start) / (alertMax - start) * count).toInt().coerceIn(0, count)
    return List(count) { i ->
        when {
            i >= lit -> LedColor.Off
            i < count * 0.4f -> LedColor.Green
            i < count * 0.7f -> LedColor.Yellow
            else -> LedColor.Red
        }
    }
}

enum class TireState { Cold, Ok, Warm, Hot }

fun tireState(tempC: Float): TireState = when {
    tempC < 60f -> TireState.Cold
    tempC < 95f -> TireState.Ok
    tempC < 110f -> TireState.Warm
    else -> TireState.Hot
}
