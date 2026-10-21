package de.stiller.gt7.ui

import de.stiller.gt7.storage.EXPORT_EXTENSION
import de.stiller.gt7.storage.ImportResult
import de.stiller.gt7.telemetry.PacketStats
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun two(n: Int) = n.toString().padStart(2, '0')

/** `yyyy-MM-dd_HH-mm-ss.gt7rec` in [zone]. */
fun recordingFileName(epochMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
    return "${t.year.toString().padStart(4, '0')}-${two(t.monthNumber)}-${two(t.dayOfMonth)}_" +
        "${two(t.hour)}-${two(t.minute)}-${two(t.second)}.gt7rec"
}

/** `gt7-dashboard_yyyy-MM-dd.gt7export` in [zone]. */
fun exportFileName(epochMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
    return "gt7-dashboard_${t.year.toString().padStart(4, '0')}-${two(t.monthNumber)}-${two(t.dayOfMonth)}.$EXPORT_EXTENSION"
}

/** `Importiert: 3 Fahrer, 2 Strecken, 41 Sitzungen (12 schon vorhanden), 5 Autonamen`. */
fun importResultText(r: ImportResult): String {
    fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
    val sessions = count(r.sessions, "Sitzung", "Sitzungen") + if (r.skippedSessions > 0) " (${r.skippedSessions} schon vorhanden)" else ""
    return "Importiert: ${count(r.drivers, "Fahrer", "Fahrer")}, ${count(r.tracks, "Strecke", "Strecken")}, $sessions, " +
        count(r.carNames, "Autoname", "Autonamen")
}

/** `Gleiche Strecke wie am dd.MM. – Name?`; the date is omitted if the track was never driven (0). */
fun suggestText(trackName: String, lastDrivenAtMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    if (lastDrivenAtMs <= 0L) return "Gleiche Strecke wie $trackName?"
    val t = Instant.fromEpochMilliseconds(lastDrivenAtMs).toLocalDateTime(zone)
    return "Gleiche Strecke wie am ${two(t.dayOfMonth)}.${two(t.monthNumber)}. – $trackName?"
}

/** `<n> Pakete/s · <m> verworfen`, plus `· <k> Schreibfehler` when recording errors occurred. */
fun diagnosticsText(stats: PacketStats): String {
    val base = "${stats.packetsPerSecond} Pakete/s · ${stats.dropped} verworfen"
    return if (stats.recordErrors > 0) "$base · ${stats.recordErrors} Schreibfehler" else base
}
