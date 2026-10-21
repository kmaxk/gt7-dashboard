package de.stiller.gt7.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import de.stiller.gt7.storage.SessionSummary

/** Rename and delete for the open session; a session being driven ([running]) cannot be deleted. */
@Composable
fun SessionActions(session: SessionSummary, running: Boolean, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var name by remember(session.id) { mutableStateOf(session.name.orEmpty()) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { name = session.name.orEmpty(); renaming = true }) { Text("Umbenennen") }
        TextButton(onClick = { deleting = true }, enabled = !running) { Text("Löschen") }
        if (running) Text("läuft gerade", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Sitzung umbenennen") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (leer = kein Name)") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(name); renaming = false }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Abbrechen") } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Sitzung löschen?") },
            text = { Text("Die Sitzung wird mit allen Runden gelöscht, auch aus der Bestenliste. Das lässt sich nicht rückgängig machen.") },
            confirmButton = {
                TextButton(onClick = { deleting = false; onDelete() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Abbrechen") } },
        )
    }
}
