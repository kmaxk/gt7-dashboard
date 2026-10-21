package de.bavaco.gt7.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Colors live in Theme.kt only, so the whole app follows the palette. */
class NoColorLiteralsTest {
    @Test
    fun noColorLiteralsOutsideTheTheme() {
        val ui = File("src/commonMain/kotlin/de/bavaco/gt7/ui")
        val found = ui.listFiles { f -> f.extension == "kt" && f.name != "Theme.kt" }.orEmpty()
            .sortedBy { it.name }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if ("Color(0x" in line) "${f.name}:${i + 1}" else null } }
        assertEquals(emptyList(), found)
    }
}
