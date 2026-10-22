package de.bavaco.gt7.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import de.bavaco.gt7.storage.validateDriverName
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.bavaco.gt7.session.ConnectionStatus
import de.bavaco.gt7.session.SessionController
import de.bavaco.gt7.storage.Driver
import de.bavaco.gt7.storage.DataTransfer
import de.bavaco.gt7.storage.EXPORT_EXTENSION
import de.bavaco.gt7.storage.FilePicker
import de.bavaco.gt7.storage.copyFile
import de.bavaco.gt7.storage.ImportException
import de.bavaco.gt7.storage.appDataDir
import de.bavaco.gt7.telemetry.readRecording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Clock

@Composable
fun SettingsScreen(
    controller: SessionController,
    transfer: DataTransfer,
    files: FilePicker,
    unit: SpeedUnit,
    tipLanguage: TipLanguage,
    coachEnabled: Boolean,
    /** Voice output; null where the platform has no voice. */
    speech: SpeechSettings?,
    onImported: () -> Unit,
    onCoachChange: (Boolean) -> Unit,
    onSpeechChange: (Boolean) -> Unit,
    onVoiceChange: (String?) -> Unit,
    onTipLanguageChange: (TipLanguage) -> Unit,
    onUnitChange: (SpeedUnit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val stats by controller.stats.collectAsState()
    val host by controller.ps5Host.collectAsState()
    val connection by controller.connection.collectAsState()
    var ip by remember { mutableStateOf(host.orEmpty()) }
    // discovery may fill in the host after the screen opened
    LaunchedEffect(host) { host?.let { ip = it } }
    val recordingName by controller.recording.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("PS5", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP-Adresse") }, singleLine = true)
            Button(shape = RaceButtonShape, onClick = { controller.connect(ip.trim()) }, enabled = ip.isNotBlank()) { Text("Verbinden") }
            OutlinedButton(shape = RaceButtonShape, onClick = { controller.discover() }, enabled = connection != ConnectionStatus.Searching) {
                Text(if (connection == ConnectionStatus.Searching) "Suche läuft…" else "PS5 suchen")
            }
        }

        DriverSettings(controller)

        Text("Einheit", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = unit == SpeedUnit.KMH, onClick = { onUnitChange(SpeedUnit.KMH) }, label = { Text("km/h") })
            FilterChip(selected = unit == SpeedUnit.MPH, onClick = { onUnitChange(SpeedUnit.MPH) }, label = { Text("mph") })
        }

        Text("Renningenieur", style = MaterialTheme.typography.titleMedium)
        Text("Sprache der Fahrtipps in der Analyse", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tipLanguage == TipLanguage.DE, onClick = { onTipLanguageChange(TipLanguage.DE) }, label = { Text("Deutsch") })
            FilterChip(selected = tipLanguage == TipLanguage.EN, onClick = { onTipLanguageChange(TipLanguage.EN) }, label = { Text("English") })
        }
        Text("Live-Tipps vor Kurven, die in der letzten Runde Zeit gekostet haben", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = coachEnabled, onClick = { onCoachChange(true) }, label = { Text("An") })
            FilterChip(selected = !coachEnabled, onClick = { onCoachChange(false) }, label = { Text("Aus") })
        }
        if (speech != null) {
            Text("Live-Tipps zusätzlich vorlesen", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = speech.enabled, onClick = { onSpeechChange(true) }, label = { Text("An") })
                FilterChip(selected = !speech.enabled, onClick = { onSpeechChange(false) }, label = { Text("Aus") })
            }
            if (speech.voices.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Stimme")
                    FilterMenu(
                        speech.voice?.takeIf { it in speech.voices } ?: "Standard",
                        listOf<Pair<String, () -> Unit>>("Standard" to { onVoiceChange(null) }) + speech.voices.map { v -> v to { onVoiceChange(v) } },
                    )
                }
            }
        }

        Text("Diagnose", style = MaterialTheme.typography.titleMedium)
        Text(diagnosticsText(stats))

        Text("Mitschnitt", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(shape = RaceButtonShape, onClick = {
                val stopped = recordingName
                if (stopped != null) {
                    controller.stopRecording()
                    // on Android the recordings folder is private: offer to keep a copy where the user can reach it
                    if (files.recordingsArePrivate) scope.launch {
                        try {
                            files.save("Mitschnitt speichern", stopped) { copyFile(Path(recordingsDir(), stopped).toString(), it) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            message = "Mitschnitt konnte nicht gespeichert werden: ${e.message}"
                        }
                    }
                } else {
                    scope.launch {
                        try {
                            val name = recordingFileName(Clock.System.now().toEpochMilliseconds())
                            val sink = withContext(Dispatchers.Default) {
                                val dir = recordingsDir()
                                SystemFileSystem.createDirectories(dir)
                                SystemFileSystem.sink(Path(dir, name)).buffered()
                            }
                            controller.startRecording(sink, name)
                            message = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            message = "Mitschnitt konnte nicht gestartet werden: ${e.message}"
                        }
                    }
                }
            }) { Text(if (recordingName != null) "Mitschnitt stoppen" else "Mitschnitt starten") }
            recordingName?.let { Text(it) }
        }
        OutlinedButton(shape = RaceButtonShape, onClick = {
            scope.launch {
                try {
                    val file = files.open("Mitschnitt abspielen", "gt7rec", recordingsDir().toString())
                    val packets = file?.let { withContext(Dispatchers.Default) { SystemFileSystem.source(Path(it)).buffered().use(::readRecording) } }
                    if (packets != null) {
                        controller.startReplay(packets)
                        message = null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = "Mitschnitt konnte nicht gelesen werden: ${e.message}"
                }
            }
        }) { Text("Mitschnitt abspielen…") }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }

        DataSettings(controller, transfer, files, onImported)
    }
}

private fun recordingsDir() = Path(appDataDir(), "recordings")

/** Export everything to a file and merge such a file back in. */
@Composable
private fun DataSettings(controller: SessionController, transfer: DataTransfer, files: FilePicker, onImported: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    fun run(block: suspend () -> String?) {
        busy = true
        scope.launch {
            status = try {
                block()?.let { it to false }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                e.message.orEmpty() to true
            } catch (e: Exception) {
                "Fehlgeschlagen: ${e.message}" to true
            } finally {
                busy = false
            }
        }
    }

    Text("Daten", style = MaterialTheme.typography.titleMedium)
    Text(
        "Export speichert Fahrer, Strecken, Runden und Autonamen in einer Datei, z. B. im Nextcloud- oder Drive-Ordner. " +
            "Import fügt eine solche Datei hinzu; schon vorhandene Sitzungen werden übersprungen.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(shape = RaceButtonShape, enabled = !busy, onClick = {
            run {
                val name = files.save("Daten exportieren", exportFileName(Clock.System.now().toEpochMilliseconds())) { path ->
                    withContext(Dispatchers.Default) { controller.exclusive { transfer.exportTo(path) } }
                }
                name?.let { "Exportiert nach $it" }
            }
        }) { Text("Exportieren…") }
        OutlinedButton(shape = RaceButtonShape, enabled = !busy, onClick = {
            run {
                val file = files.open("Daten importieren", EXPORT_EXTENSION) ?: return@run null
                val result = withContext(Dispatchers.Default) { controller.importData { transfer.importFrom(file) } }
                onImported()
                importResultText(result)
            }
        }) { Text("Importieren…") }
        if (busy) Text("Läuft…")
    }
    status?.let { (text, error) ->
        Text(text, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
    }
}

/** Rename and delete drivers; deleting one with laps asks first. The active and the last driver stay. */
@Composable
private fun DriverSettings(controller: SessionController) {
    val drivers by controller.drivers.collectAsState()
    val withSessions by controller.driversWithSessions.collectAsState()
    var editing by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    val active by controller.activeDriver.collectAsState()
    var confirmDelete by remember { mutableStateOf<Driver?>(null) }
    Text("Fahrer", style = MaterialTheme.typography.titleMedium)
    drivers.forEach { d ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editing == d.id) {
                val valid = validateDriverName(name, drivers.filter { it.id != d.id })
                OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, singleLine = true, isError = valid == null)
                Button(shape = RaceButtonShape, onClick = { valid?.let { controller.renameDriver(d.id, it) }; editing = null }, enabled = valid != null) { Text("Speichern") }
                TextButton(onClick = { editing = null }) { Text("Abbrechen") }
            } else {
                Text(d.name, Modifier.width(200.dp))
                OutlinedButton(shape = RaceButtonShape, onClick = { editing = d.id; name = d.name }) { Text("Umbenennen") }
                val isActive = d.id == active?.id
                OutlinedButton(shape = RaceButtonShape, 
                    onClick = { if (d.id in withSessions) confirmDelete = d else controller.deleteDriver(d.id) },
                    enabled = !isActive && drivers.size > 1,
                ) { Text("Löschen") }
                if (isActive) {
                    Text("fährt gerade – nicht löschbar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
    confirmDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Fahrer löschen?") },
            text = { Text("${d.name} wird mit allen gefahrenen Runden gelöscht, auch aus der Bestenliste. Das lässt sich nicht rückgängig machen.") },
            confirmButton = {
                TextButton(onClick = { controller.deleteDriver(d.id); confirmDelete = null }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }
}
