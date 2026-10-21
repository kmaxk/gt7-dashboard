package de.bavaco.gt7.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.bavaco.gt7.session.TrackQuestion

@Composable
fun TrackDialog(
    question: TrackQuestion,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onName: (String) -> Unit,
) {
    when (question) {
        is TrackQuestion.Suggest -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Strecke erkannt") },
            text = { Text(suggestText(question.track.name, question.lastDrivenAtMs)) },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Ja") } },
            dismissButton = { TextButton(onClick = onReject) { Text("Nein") } },
        )
        TrackQuestion.AskName -> {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Neue Strecke") },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name der Strecke") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onName(name.trim()) }, enabled = name.isNotBlank()) { Text("Speichern") }
                },
            )
        }
    }
}
