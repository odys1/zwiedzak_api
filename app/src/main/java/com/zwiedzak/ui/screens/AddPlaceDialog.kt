package com.zwiedzak.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zwiedzak.data.model.CreatePlaceRequest
import com.zwiedzak.ui.PlaceActionState

@Composable
fun AddPlaceDialog(
    initialLatitude: Double,
    initialLongitude: Double,
    actionState: PlaceActionState,
    onDismiss: () -> Unit,
    onSubmit: (CreatePlaceRequest) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf(initialLatitude.toString()) }
    var longitude by remember { mutableStateOf(initialLongitude.toString()) }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var hasAccommodation by remember { mutableStateOf(false) }
    var hasFood by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf("private") }
    var confirmDistinct by remember { mutableStateOf(false) }

    val parsedLatitude = latitude.toDoubleOrNull()
    val parsedLongitude = longitude.toDoubleOrNull()
    val canSubmit = name.trim().length in 2..150 &&
        category.trim().length in 2..64 &&
        parsedLatitude != null && parsedLatitude in -90.0..90.0 &&
        parsedLongitude != null && parsedLongitude in -180.0..180.0 &&
        actionState !is PlaceActionState.Loading

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj miejsce") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(150) },
                    label = { Text("Nazwa (2–150 znaków)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(64) },
                    label = { Text("Kategoria (np. zamek)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Szerokość") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Długość") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adres (opcjonalnie)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Opis (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Widoczność", style = MaterialTheme.typography.titleSmall)
                listOf("private" to "Prywatne", "public" to "Publiczne").forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = visibility == value,
                            onClick = { visibility = value }
                        )
                        Text(label)
                    }
                }
                if (visibility == "public") {
                    Text(
                        "Publiczna propozycja wymaga zatwierdzenia przez administratora.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasAccommodation,
                        onCheckedChange = { hasAccommodation = it }
                    )
                    Text("Jest hotel / nocleg")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasFood,
                        onCheckedChange = { hasFood = it }
                    )
                    Text("Jest restauracja / jedzenie")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = confirmDistinct,
                        onCheckedChange = { confirmDistinct = it }
                    )
                    Text("Potwierdzam, że to odrębne miejsce")
                }
                when (actionState) {
                    is PlaceActionState.Error -> Text(
                        actionState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    is PlaceActionState.Loading -> CircularProgressIndicator()
                    else -> Unit
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = {
                    onSubmit(
                        CreatePlaceRequest(
                            name = name.trim(),
                            category = category.trim(),
                            latitude = parsedLatitude!!,
                            longitude = parsedLongitude!!,
                            address = address.trim().takeIf { it.isNotEmpty() },
                            description = description.trim().takeIf { it.isNotEmpty() },
                            hasAccommodation = hasAccommodation,
                            hasFood = hasFood,
                            visibility = visibility,
                            confirmDistinct = confirmDistinct
                        )
                    )
                }
            ) {
                Text("Dodaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = actionState !is PlaceActionState.Loading) {
                Text("Anuluj")
            }
        }
    )
}
