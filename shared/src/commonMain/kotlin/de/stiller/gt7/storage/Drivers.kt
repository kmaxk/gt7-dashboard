package de.stiller.gt7.storage

data class Driver(val id: Long, val name: String)

const val DEFAULT_DRIVER_NAME = "Fahrer 1"

/** Trimmed [name], or null if it is empty or already taken (ignoring case). */
fun validateDriverName(name: String, existing: List<Driver>): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || existing.any { it.name.equals(trimmed, ignoreCase = true) }) return null
    return trimmed
}
