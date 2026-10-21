package de.stiller.gt7.ui

import kotlin.math.abs
import kotlin.math.roundToInt

enum class SpeedUnit { KMH, MPH }

private const val NO_TIME = "–:––.–––"

fun formatLapTime(ms: Int?): String {
    if (ms == null || ms <= 0) return NO_TIME
    val minutes = ms / 60_000
    val seconds = (ms / 1000) % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}.${(ms % 1000).toString().padStart(3, '0')}"
}

fun formatDelta(ms: Int?): String {
    if (ms == null) return "–"
    val sign = if (ms < 0) "−" else "+"
    val a = abs(ms)
    return "$sign${a / 1000}.${(a % 1000).toString().padStart(3, '0')}"
}

fun formatSpeed(mps: Float, unit: SpeedUnit): String {
    val factor = if (unit == SpeedUnit.KMH) 3.6f else 2.23694f
    return (mps * factor).roundToInt().toString()
}

/** Common replacement for JVM-only String.format("%.Nf"). */
fun formatDecimal(value: Float, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return "–"
    var scale = 1
    repeat(decimals) { scale *= 10 }
    val scaled = (abs(value) * scale).roundToInt()
    val sign = if (value < 0 && scaled != 0) "-" else ""
    if (decimals == 0) return "$sign$scaled"
    return "$sign${scaled / scale}.${(scaled % scale).toString().padStart(decimals, '0')}"
}
