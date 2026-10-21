package de.stiller.gt7.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.stiller.gt7.session.Opponent
import de.stiller.gt7.session.ReferenceInfo
import de.stiller.gt7.storage.Driver
import de.stiller.gt7.storage.validateDriverName

/** Drivers, the active one, the chosen opponent and the loaded reference lap. */
data class DriverSelection(
    val drivers: List<Driver>,
    val active: Driver?,
    val opponent: Opponent,
    val reference: ReferenceInfo?,
)

val GhostBlue = Color(0xFF4FC3F7)
private const val SELF_LABEL = "Meine Bestzeit"

/** "Fahrer ▾" and "Gegner ▾" chips with menus; both menus can create a driver. */
@Composable
fun DriverPicker(
    selection: DriverSelection,
    scale: Float,
    onSelectDriver: (Long) -> Unit,
    onSelectOpponent: (Opponent) -> Unit,
    onCreateDriver: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    val active = selection.active
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        PickerChip("Fahrer", active?.name ?: "–", Color(0xFFECEFF1), scale) { close ->
            selection.drivers.forEach { d ->
                MenuEntry(d.name, d.id == active?.id) { onSelectDriver(d.id); close() }
            }
            HorizontalDivider()
            MenuEntry("+ Neuer Fahrer", false) { creating = true; close() }
        }
        val opponentName = when (val o = selection.opponent) {
            Opponent.Self -> SELF_LABEL
            is Opponent.Of -> o.driver.name
        }
        PickerChip("Gegner", opponentName, GhostBlue, scale) { close ->
            MenuEntry(SELF_LABEL, selection.opponent == Opponent.Self) { onSelectOpponent(Opponent.Self); close() }
            selection.drivers.filter { it.id != active?.id }.forEach { d ->
                MenuEntry(d.name, (selection.opponent as? Opponent.Of)?.driver?.id == d.id) { onSelectOpponent(Opponent.Of(d)); close() }
            }
            HorizontalDivider()
            MenuEntry("+ Neuer Fahrer", false) { creating = true; close() }
        }
        Text(
            "Fahrerwechsel beendet die laufende Sitzung",
            fontSize = 12.sp * scale,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
    if (creating) NewDriverDialog(selection.drivers, onDismiss = { creating = false }) {
        onCreateDriver(it)
        creating = false
    }
}

@Composable
private fun PickerChip(title: String, value: String, dot: Color, scale: Float, menu: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Panel).border(1.dp, Color(0xFF2A2D34), RoundedCornerShape(50))
                .clickable { open = true }.padding(horizontal = 12.dp * scale, vertical = 5.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(8.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 14.sp * scale, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Box(Modifier.size(10.dp * scale).clip(CircleShape).background(dot))
            Text(value, fontSize = 14.sp * scale, fontWeight = FontWeight.SemiBold)
            Text("▾", fontSize = 14.sp * scale, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
    }
}

@Composable
private fun MenuEntry(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified) },
        onClick = onClick,
    )
}

@Composable
private fun NewDriverDialog(existing: List<Driver>, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val valid = validateDriverName(name, existing)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuer Fahrer") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = { Text("Name") },
                singleLine = true,
                isError = name.isNotBlank() && valid == null,
                supportingText = { if (name.isNotBlank() && valid == null) Text("Diesen Namen gibt es schon") },
            )
        },
        confirmButton = { TextButton(onClick = { valid?.let(onCreate) }, enabled = valid != null) { Text("Anlegen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
