package de.bavaco.gt7.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** AWT file dialogs; recordings stay reachable in the app data folder. */
class AwtFilePicker : FilePicker {
    override val recordingsArePrivate = false

    override suspend fun open(title: String, extension: String, startDir: String?): String? = withContext(Dispatchers.IO) {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        startDir?.let { dialog.directory = File(it).apply { mkdirs() }.absolutePath }
        dialog.setFilenameFilter { _, name -> name.endsWith(".$extension") }
        dialog.isVisible = true
        dialog.file?.let { File(dialog.directory, it).absolutePath }
    }

    override suspend fun save(title: String, suggestedName: String, write: suspend (path: String) -> Unit): String? {
        val target = withContext(Dispatchers.IO) {
            val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
            dialog.file = suggestedName
            dialog.isVisible = true
            val ext = suggestedName.substringAfterLast('.', "")
            dialog.file?.let { File(dialog.directory, if (ext.isEmpty()) it else withExtension(it, ext)) }
        } ?: return null
        write(target.absolutePath)
        return target.name
    }
}
