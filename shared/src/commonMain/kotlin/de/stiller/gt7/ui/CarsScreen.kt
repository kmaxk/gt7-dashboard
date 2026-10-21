package de.stiller.gt7.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.stiller.gt7.storage.CarCatalog
import de.stiller.gt7.storage.CarInfo
import de.stiller.gt7.storage.DrivenCar
import de.stiller.gt7.storage.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_RESULTS = 50

/** Cars tab: driven cars, search over the car list, own names (rename, reset, add an unknown code). */
@Composable
fun CarsScreen(repo: SessionRepository, cars: CarCatalog, onNamesChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    // laps driven meanwhile change counts and bring new cars
    val sessions by remember { repo.sessions() }.collectAsState(emptyList())
    var driven by remember { mutableStateOf<List<DrivenCar>>(emptyList()) }
    LaunchedEffect(sessions) { driven = withContext(Dispatchers.Default) { repo.drivenCars() } }
    var query by remember { mutableStateOf("") }
    val results = remember(query, cars) { if (query.isBlank()) emptyList() else cars.search(query).sortedBy { it.name.lowercase() } }

    fun change(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.Default) { block() }
            onNamesChanged()
        }
    }
    val rename: (Int, String) -> Unit = { code, name -> change { repo.setCarName(code, name.trim()) } }
    val reset: (Int) -> Unit = { code -> change { repo.resetCarName(code) } }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Gefahren", style = MaterialTheme.typography.titleMedium) }
        if (driven.isEmpty()) item { Text("Noch keine Sitzung gefahren.", style = MaterialTheme.typography.bodySmall) }
        items(driven, key = { "driven-${it.carCode}" }) { d ->
            CarRow(cars.info(d.carCode), drivenCarFacts(d), rename, reset)
        }
        item {
            Text("Alle Autos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Suche nach Name, Hersteller oder ID") },
                modifier = if (LocalCompact.current) Modifier.fillMaxWidth() else Modifier.width(420.dp),
            )
        }
        items(results.take(MAX_RESULTS), key = { "search-${it.code}" }) { c -> CarRow(c, "ID ${c.code}", rename, reset) }
        if (results.size > MAX_RESULTS) item {
            Text("… und ${results.size - MAX_RESULTS} weitere – Suche verfeinern.", style = MaterialTheme.typography.bodySmall)
        }
        item { AddCar(cars, rename) }
    }
}

@Composable
private fun CarRow(car: CarInfo, facts: String, onRename: (Int, String) -> Unit, onReset: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(car.name) }
    val unknown = !car.custom && car.listName == null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Panel).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, isError = name.isBlank())
                    Button(shape = RaceButtonShape, onClick = { onRename(car.code, name); editing = false }, enabled = name.isNotBlank()) { Text("Speichern") }
                    TextButton(onClick = { editing = false }) { Text("Abbrechen") }
                }
            } else {
                Text(car.name, style = MaterialTheme.typography.titleSmall, color = if (unknown) WarnAmber else MaterialTheme.colorScheme.onSurface)
            }
            Text("$facts · ${carOrigin(car)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        if (!editing) {
            OutlinedButton(shape = RaceButtonShape, onClick = { editing = true; name = if (unknown) "" else car.name }) { Text(if (unknown) "Benennen" else "Umbenennen") }
            if (car.custom) TextButton(onClick = { onReset(car.code) }) { Text(if (car.listName != null) "Listenname" else "Entfernen") }
        }
    }
}

/** Names a car the list does not know (or overrides one) by its code. */
@Composable
private fun AddCar(cars: CarCatalog, onRename: (Int, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val parsed = parseCarCode(code)
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Auto hinzufügen", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code, onValueChange = { code = it.filter(Char::isDigit).take(9) }, singleLine = true,
                label = { Text("ID") }, modifier = Modifier.width(120.dp),
            )
            OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("Name") })
            Button(shape = RaceButtonShape, onClick = { parsed?.let { onRename(it, name) }; code = ""; name = "" }, enabled = parsed != null && name.isNotBlank()) {
                Text("Speichern")
            }
        }
        parsed?.let { Text("ID $it ist bisher „${cars.name(it)}“.", style = MaterialTheme.typography.bodySmall) }
    }
}
