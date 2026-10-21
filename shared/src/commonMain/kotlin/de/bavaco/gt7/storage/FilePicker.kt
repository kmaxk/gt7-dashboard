package de.bavaco.gt7.storage

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Lets the user choose files; each platform shows its own dialogs. */
interface FilePicker {
    /** Recordings land in a folder the user cannot reach, so the app offers to save each one after stopping. */
    val recordingsArePrivate: Boolean

    /** Local path of a file the user chose to read; null if cancelled. [extension] (without dot) filters where the platform can. */
    suspend fun open(title: String, extension: String, startDir: String? = null): String?

    /** Asks for a target, lets [write] fill a local file and stores it there; the target's display name, null if cancelled. */
    suspend fun save(title: String, suggestedName: String, write: suspend (path: String) -> Unit): String?
}

/** [name] ending in `.extension`. */
fun withExtension(name: String, extension: String): String = if (name.endsWith(".$extension")) name else "$name.$extension"

/** Lets [write] fill [tempPath], hands it to [store] and deletes it afterwards, also on failure. */
suspend fun saveVia(tempPath: String, write: suspend (String) -> Unit, store: suspend (String) -> Unit) {
    try {
        write(tempPath)
        store(tempPath)
    } finally {
        val temp = Path(tempPath)
        if (SystemFileSystem.exists(temp)) SystemFileSystem.delete(temp)
    }
}

fun copyFile(from: String, to: String) {
    SystemFileSystem.source(Path(from)).buffered().use { src -> SystemFileSystem.sink(Path(to)).buffered().use { src.transferTo(it) } }
}
