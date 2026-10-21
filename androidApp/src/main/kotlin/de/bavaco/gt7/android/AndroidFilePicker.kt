package de.bavaco.gt7.android

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import de.bavaco.gt7.storage.FilePicker
import de.bavaco.gt7.storage.saveVia
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * File choosing through the storage access framework. The chosen documents are content URIs, so a file to read is
 * copied into the cache first and a file to save is written to the cache and copied into the URI afterwards.
 * Must be created in onCreate, before the activity is started.
 */
class AndroidFilePicker(private val activity: ComponentActivity) : FilePicker {
    override val recordingsArePrivate = true

    private var pendingOpen: CompletableDeferred<Uri?>? = null
    private var pendingSave: CompletableDeferred<Uri?>? = null
    private val openLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingOpen?.complete(uri)
    }
    private val saveLauncher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        pendingSave?.complete(uri)
    }

    // the document picker filters by MIME type only; the caller checks the content (e.g. the SQLite header)
    override suspend fun open(title: String, extension: String, startDir: String?): String? {
        val uri = withContext(Dispatchers.Main) {
            CompletableDeferred<Uri?>().also { pendingOpen = it; openLauncher.launch(arrayOf("*/*")) }
        }.await() ?: return null
        return withContext(Dispatchers.IO) {
            activity.cacheDir.listFiles { f -> f.name.startsWith("open-") }?.forEach { it.delete() }
            val copy = File(activity.cacheDir, "open-${Random.nextLong().toULong()}.$extension")
            activity.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
            copy.absolutePath
        }
    }

    override suspend fun save(title: String, suggestedName: String, write: suspend (path: String) -> Unit): String? {
        val uri = withContext(Dispatchers.Main) {
            CompletableDeferred<Uri?>().also { pendingSave = it; saveLauncher.launch(suggestedName) }
        }.await() ?: return null
        // Android's SQLite may leave a -journal next to an exported file; clear what earlier saves left behind
        withContext(Dispatchers.IO) { activity.cacheDir.listFiles { f -> f.name.startsWith("save-") }?.forEach { it.delete() } }
        val temp = File(activity.cacheDir, "save-${Random.nextLong().toULong()}").absolutePath
        saveVia(temp, write) { path ->
            withContext(Dispatchers.IO) {
                activity.contentResolver.openOutputStream(uri, "wt")!!.use { out -> File(path).inputStream().use { it.copyTo(out) } }
            }
        }
        return displayName(uri) ?: suggestedName
    }

    private suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }
}
