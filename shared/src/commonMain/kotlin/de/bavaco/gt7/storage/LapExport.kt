package de.bavaco.gt7.storage

import de.bavaco.gt7.session.LapSample
import kotlin.math.abs
import kotlin.math.roundToLong

/** What a lap file says about its lap besides the samples. */
data class LapMeta(
    val track: String?,
    val car: String?,
    val driver: String?,
    val sessionStartedAtMs: Long,
    val lapNumber: Int,
    val timeMs: Int,
    val valid: Boolean,
)

private val COLUMNS = listOf("time_ms", "distance_m", "speed_kmh", "throttle_pct", "brake_pct", "gear", "rpm", "x", "z", "boost_bar")

/** Fixed decimals with a dot, independent of the locale. */
private fun fixed(value: Float, decimals: Int): String {
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = (abs(value.toDouble()) * scale).roundToLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return "$sign$scaled"
    return "$sign${scaled / scale}.${(scaled % scale).toString().padStart(decimals, '0')}"
}

private fun values(s: LapSample): List<String> = listOf(
    "${s.timeMs}", fixed(s.distanceM, 2), fixed(s.speedMps * 3.6f, 1), fixed(s.throttle * 100f, 1), fixed(s.brake * 100f, 1),
    "${s.gear}", fixed(s.rpm, 0), fixed(s.x, 2), fixed(s.z, 2), fixed(s.boostBar, 2),
)

/** One line per sample under a header; speeds in km/h, pedals in percent. */
fun lapCsv(samples: List<LapSample>): String = buildString {
    append(COLUMNS.joinToString(",")).append('\n')
    for (s in samples) append(values(s).joinToString(",")).append('\n')
}

private fun jsonString(text: String?): String {
    if (text == null) return "null"
    val escaped = buildString {
        for (c in text) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
    }
    return "\"$escaped\""
}

/** The lap with [meta] and the same sample fields as [lapCsv]. */
fun lapJson(meta: LapMeta, samples: List<LapSample>): String = buildString {
    append("{\"track\":").append(jsonString(meta.track))
    append(",\"car\":").append(jsonString(meta.car))
    append(",\"driver\":").append(jsonString(meta.driver))
    append(",\"session_started_ms\":").append(meta.sessionStartedAtMs)
    append(",\"lap\":").append(meta.lapNumber)
    append(",\"time_ms\":").append(meta.timeMs)
    append(",\"valid\":").append(meta.valid)
    append(",\"samples\":[")
    samples.forEachIndexed { i, s ->
        if (i > 0) append(',')
        append(COLUMNS.zip(values(s)).joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" })
    }
    append("]}")
}

private val UMLAUTS = mapOf('ä' to "ae", 'ö' to "oe", 'ü' to "ue", 'Ä' to "Ae", 'Ö' to "Oe", 'Ü' to "Ue", 'ß' to "ss")

/** Letters and digits only, everything else becomes a single `-`. */
private fun safe(text: String): String =
    text.map { UMLAUTS[it] ?: it.toString() }.joinToString("")
        .replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')

/** `Sugo-IRC_Runde-3_1-25-797.csv`; without a track `Runde-3_…`, [suffix] before the extension. */
fun lapFileName(track: String?, lapNumber: Int, timeMs: Int, extension: String, suffix: String? = null): String {
    val time = "${timeMs / 60_000}-${((timeMs / 1000) % 60).toString().padStart(2, '0')}-${(timeMs % 1000).toString().padStart(3, '0')}"
    val prefix = track?.let { safe(it) }?.takeIf { it.isNotEmpty() }?.let { "${it}_" } ?: ""
    return "${prefix}Runde-${lapNumber}_$time${suffix?.let { "_$it" } ?: ""}.$extension"
}
