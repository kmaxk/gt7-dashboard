package de.bavaco.gt7.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.dp
import de.bavaco.gt7.session.LapSample
import de.bavaco.gt7.storage.CarCatalog
import de.bavaco.gt7.storage.FilePicker
import de.bavaco.gt7.storage.LapMeta
import de.bavaco.gt7.storage.LapSummary
import de.bavaco.gt7.storage.SessionSummary
import de.bavaco.gt7.storage.lapCsv
import de.bavaco.gt7.storage.lapFileName
import de.bavaco.gt7.storage.lapJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.write

private fun writeBytes(path: String, bytes: ByteArray) {
    SystemFileSystem.sink(Path(path)).buffered().use { it.write(bytes) }
}

/** Saves lap A as CSV or JSON and the visible analysis ([capture]) as PNG; [onMessage] reports the result. */
@Composable
fun ExportActions(
    lap: LapSummary,
    session: SessionSummary?,
    samples: List<LapSample>,
    cars: CarCatalog,
    files: FilePicker,
    capture: GraphicsLayer,
    onMessage: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun save(kind: String, name: String, bytes: suspend () -> ByteArray) {
        scope.launch {
            onMessage(null)
            try {
                val data = bytes()
                val target = files.save("$kind speichern", name) { path -> withContext(Dispatchers.Default) { writeBytes(path, data) } }
                if (target != null) onMessage("Gespeichert: $target")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onMessage("Speichern fehlgeschlagen: ${e.message ?: e::class.simpleName}")
            }
        }
    }
    val track = session?.trackName
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Runde A:", style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { save("CSV", lapFileName(track, lap.number, lap.timeMs, "csv")) { lapCsv(samples).encodeToByteArray() } }) { Text("CSV…") }
        TextButton(onClick = {
            val meta = LapMeta(track, session?.let { cars.name(it.carCode) }, session?.driverName, session?.startedAtMs ?: 0L, lap.number, lap.timeMs, lap.isValid)
            save("JSON", lapFileName(track, lap.number, lap.timeMs, "json")) { lapJson(meta, samples).encodeToByteArray() }
        }) { Text("JSON…") }
        TextButton(onClick = {
            save("PNG", lapFileName(track, lap.number, lap.timeMs, "png", suffix = "Analyse")) {
                val image = capture.toImageBitmap()
                withContext(Dispatchers.Default) { encodePng(image) }
            }
        }) { Text("PNG…") }
    }
}
