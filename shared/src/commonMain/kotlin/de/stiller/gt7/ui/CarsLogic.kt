package de.stiller.gt7.ui

import de.stiller.gt7.storage.CarInfo
import de.stiller.gt7.storage.DrivenCar
import kotlinx.datetime.TimeZone

/** A car code typed by the user: a positive number, else null. */
fun parseCarCode(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it > 0 }

/** Where the shown name comes from: `Liste · Toyota`, `eigener Name · Liste: GR Supra`, `unbekannt – bitte benennen`. */
fun carOrigin(car: CarInfo): String = when {
    car.custom -> listOfNotNull("eigener Name", car.listName?.let { "Liste: $it" }).joinToString(" · ")
    car.listName != null -> listOfNotNull("Liste", car.maker).joinToString(" · ")
    else -> "unbekannt – bitte benennen"
}

/** `ID 12 · 3 Runden · zuletzt 05.03.2026 07:08`. */
fun drivenCarFacts(car: DrivenCar, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    "ID ${car.carCode} · ${if (car.lapCount == 1) "1 Runde" else "${car.lapCount} Runden"} · zuletzt ${formatDateTime(car.lastDrivenAtMs, zone)}"
