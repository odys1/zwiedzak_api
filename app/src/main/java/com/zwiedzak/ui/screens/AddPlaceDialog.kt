package com.zwiedzak.ui.screens

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.zwiedzak.data.model.CreatePlaceRequest
import com.zwiedzak.data.model.Place
import com.zwiedzak.ui.CategoriesState
import com.zwiedzak.ui.PlaceActionState
import com.zwiedzak.ui.VisitHistoryState
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaceDialog(
    initialLatitude: Double,
    initialLongitude: Double,
    actionState: PlaceActionState,
    categoriesState: CategoriesState,
    onDismiss: () -> Unit,
    onRetryCategories: () -> Unit,
    onSubmit: (CreatePlaceRequest, Uri?, String, Boolean) -> Unit,
    editPlace: Place? = null,
    isAuthenticated: Boolean = true,
    historyState: VisitHistoryState = VisitHistoryState.Loading,
    onRetryHistory: () -> Unit = {},
    onAuthClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val canEdit = editPlace == null || isAuthenticated
    var name by remember { mutableStateOf(editPlace?.name.orEmpty()) }
    var categoryCode by remember { mutableStateOf(editPlace?.category.orEmpty()) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var latitude by remember { mutableStateOf(editPlace?.latitude?.toString() ?: if (editPlace == null) initialLatitude.toString() else "") }
    var longitude by remember { mutableStateOf(editPlace?.longitude?.toString() ?: if (editPlace == null) initialLongitude.toString() else "") }
    var showLocationPicker by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(editPlace?.address.orEmpty()) }
    var description by remember { mutableStateOf(editPlace?.description.orEmpty()) }
    var hasAccommodation by remember { mutableStateOf(editPlace?.hasAccommodation == 1) }
    var hasFood by remember { mutableStateOf(editPlace?.hasFood == 1) }
    var visited by remember { mutableStateOf(editPlace?.visitState?.isVisited == true) }
    var visibility by remember {
        mutableStateOf(if (editPlace == null || !editPlace.canEdit) "private" else editPlace.visibility.orEmpty())
    }
    var confirmDistinct by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageCaption by remember { mutableStateOf("") }
    var cameraImagePath by remember { mutableStateOf<String?>(null) }
    var historyExpanded by remember { mutableStateOf(false) }
    var selectedVisit by remember { mutableStateOf("") }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedImageUri = uri
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captured ->
        val path = cameraImagePath
        if (captured && path != null) {
            selectedImageUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
        } else {
            path?.let { File(it).delete() }
            cameraImagePath = null
        }
    }

    LaunchedEffect(actionState) {
        if (actionState is PlaceActionState.Success) {
            cameraImagePath?.let { File(it).delete() }
            cameraImagePath = null
        }
    }

    val parsedLatitude = latitude.toDoubleOrNull()
    val parsedLongitude = longitude.toDoubleOrNull()
    val categories = (categoriesState as? CategoriesState.Success)?.items.orEmpty()
    val selectedCategory = categories.firstOrNull { it.code == categoryCode }
    val canSubmit = isAuthenticated && (!canEdit || (name.trim().length in 2..150 &&
        selectedCategory != null &&
        parsedLatitude != null && parsedLatitude in -90.0..90.0 &&
        parsedLongitude != null && parsedLongitude in -180.0..180.0)) &&
        actionState !is PlaceActionState.Loading

    AlertDialog(
        onDismissRequest = {
            cameraImagePath?.let { File(it).delete() }
            onDismiss()
        },
        title = { Text(if (editPlace == null) "Dodaj miejsce" else "Edycja miejsca") },
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
                    readOnly = !canEdit,
                    label = { Text("Nazwa (2–150 znaków)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                when (val state = categoriesState) {
                    CategoriesState.Idle, CategoriesState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is CategoriesState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetryCategories) {
                            Text("Spróbuj ponownie")
                        }
                    }
                    is CategoriesState.Success -> {
                        ExposedDropdownMenuBox(
                            expanded = categoryMenuExpanded,
                            onExpandedChange = { if (canEdit) categoryMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedCategory?.name.orEmpty(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Kategoria") },
                                placeholder = { Text("Wybierz kategorię") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = categoryMenuExpanded
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = categoryMenuExpanded,
                                onDismissRequest = { categoryMenuExpanded = false }
                            ) {
                                state.items.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            categoryCode = category.code
                                            categoryMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Text("Zdjęcie (opcjonalnie)", style = MaterialTheme.typography.titleSmall)
                editPlace?.images.orEmpty().forEach { image ->
                    AsyncImage(model = image.imageUrl, contentDescription = image.caption ?: "Zdjęcie miejsca",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            galleryPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        enabled = isAuthenticated && actionState !is PlaceActionState.Loading
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Wybierz zdjęcie z galerii"
                        )
                    }
                    IconButton(
                        onClick = {
                            var cameraFile: File? = null
                            try {
                                cameraFile = File.createTempFile(
                                    "zwiedzak-new-place-",
                                    ".jpg",
                                    context.cacheDir
                                )
                                val cameraUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    cameraFile
                                )
                                cameraImagePath = cameraFile.absolutePath
                                cameraLauncher.launch(cameraUri)
                            } catch (e: IOException) {
                                cameraFile?.delete()
                                cameraImagePath = null
                                Toast.makeText(
                                    context,
                                    "Nie udało się przygotować zdjęcia z aparatu.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: ActivityNotFoundException) {
                                cameraFile?.delete()
                                cameraImagePath = null
                                Toast.makeText(
                                    context,
                                    "Nie znaleziono aplikacji aparatu.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = isAuthenticated && actionState !is PlaceActionState.Loading
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Zrób zdjęcie aparatem"
                        )
                    }
                }
                selectedImageUri?.let { uri ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Podgląd wybranego zdjęcia",
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 160.dp)
                        )
                        IconButton(
                            onClick = {
                                cameraImagePath?.let { File(it).delete() }
                                cameraImagePath = null
                                selectedImageUri = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Usuń wybrane zdjęcie"
                            )
                        }
                    }
                    OutlinedTextField(
                        value = imageCaption,
                        onValueChange = { imageCaption = it.take(255) },
                        label = { Text("Podpis zdjęcia (opcjonalnie)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        readOnly = !canEdit,
                        label = { Text("Szerokość") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        readOnly = !canEdit,
                        label = { Text("Długość") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = { showLocationPicker = true },
                    enabled = canEdit && parsedLatitude != null && parsedLatitude in -90.0..90.0 &&
                        parsedLongitude != null && parsedLongitude in -180.0..180.0
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Text("Wybierz położenie na mapie")
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    readOnly = !canEdit,
                    label = { Text("Adres (opcjonalnie)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    readOnly = !canEdit,
                    label = { Text("Opis (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Widoczność", style = MaterialTheme.typography.titleSmall)
                if (editPlace != null && editPlace.canEdit && visibility.isBlank()) {
                    Text("Brak informacji o widoczności.", style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("private" to "Prywatne", "public" to "Publiczne")
                        .forEach { (value, label) ->
                        RadioButton(
                            selected = visibility == value,
                            onClick = { visibility = value },
                            enabled = (editPlace == null || (!editPlace.canEdit && isAuthenticated)) &&
                                actionState !is PlaceActionState.Loading
                        )
                        Text(label)
                    }
                    if (editPlace != null && !editPlace.canEdit && isAuthenticated) {
                        Text(
                            if (visibility == "public") {
                                "Zmiany danych zostaną zapisane we wspólnym miejscu i będą widoczne dla wszystkich."
                            } else {
                                "Zmiany danych utworzą Twoją prywatną kopię. Sam zapis wizyty nie tworzy kopii."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (visibility == "public" && editPlace == null) {
                    Text(
                        "Publiczna propozycja wymaga zatwierdzenia przez administratora.",
                        style = MaterialTheme.typography.bodySmall
                    )

                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = visited,
                        onCheckedChange = { visited = it },
                        enabled = isAuthenticated && actionState !is PlaceActionState.Loading
                    )
                    Text("Oznacz jako odwiedzone")
                }
                if (editPlace != null && isAuthenticated && visited) {
                    Text(
                        "Zapis z zaznaczonym polem doda kolejną wizytę.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (editPlace != null && isAuthenticated) {
                    when (val history = historyState) {
                        VisitHistoryState.Loading -> Text("Pobieranie historii wizyt...")
                        is VisitHistoryState.Error -> {
                            Text(history.message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRetryHistory) { Text("Ponów pobieranie historii") }
                        }
                        is VisitHistoryState.Success -> {
                            ExposedDropdownMenuBox(expanded = historyExpanded, onExpandedChange = { historyExpanded = it }) {
                                OutlinedTextField(
                                    value = selectedVisit,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Daty wizyt") },
                                    placeholder = { Text(if (history.visits.isEmpty()) "Brak wizyt" else "Wybierz datę") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = historyExpanded) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = historyExpanded, onDismissRequest = { historyExpanded = false }) {
                                    history.visits.forEach { visit ->
                                        val formatted = formatVisitTime(visit.visitedAt)
                                        DropdownMenuItem(text = { Text(formatted) }, onClick = {
                                            selectedVisit = formatted
                                            historyExpanded = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                if (editPlace != null && !isAuthenticated) {
                    TextButton(onClick = onAuthClick) { Text("Zaloguj się, aby zapisać status wizyty") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasAccommodation,
                        onCheckedChange = { hasAccommodation = it },
                        enabled = canEdit
                    )
                    Text("Jest hotel / nocleg")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasFood,
                        onCheckedChange = { hasFood = it },
                        enabled = canEdit
                    )
                    Text("Jest restauracja / jedzenie")
                }
                if (editPlace == null) Row(verticalAlignment = Alignment.CenterVertically) {
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
                    is PlaceActionState.Success -> Text(actionState.message, color = MaterialTheme.colorScheme.primary)
                    is PlaceActionState.EditConflict -> Text("Możliwe duplikaty:\n${actionState.details}", color = MaterialTheme.colorScheme.error)
                    is PlaceActionState.EditUncertain -> Text(
                        "Nie wiadomo, czy zapis się powiódł. Ponowienie może dopisać drugą wizytę.\n${actionState.details}",
                        color = MaterialTheme.colorScheme.error
                    )
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
                            category = selectedCategory?.code ?: categoryCode,
                            latitude = parsedLatitude ?: 0.0,
                            longitude = parsedLongitude ?: 0.0,
                            address = address.trim().takeIf { it.isNotEmpty() },
                            description = description.trim().takeIf { it.isNotEmpty() },
                            hasAccommodation = hasAccommodation,
                            hasFood = hasFood,
                            visibility = visibility,
                            confirmDistinct = confirmDistinct || actionState is PlaceActionState.EditConflict,
                            visited = visited
                        ),
                        selectedImageUri,
                        imageCaption,
                        editPlace != null && !editPlace.canEdit && visibility == "public"
                    )
                }
            ) {
                Text(when {
                    actionState is PlaceActionState.EditConflict -> "Potwierdź i zapisz"
                    actionState is PlaceActionState.EditUncertain -> "Zapisz ponownie"
                    editPlace != null -> "Zapisz"
                    else -> "Dodaj"
                })
            }

        },
        dismissButton = {
            TextButton(
                onClick = {
                    cameraImagePath?.let { File(it).delete() }
                    onDismiss()
                },
                enabled = actionState !is PlaceActionState.Loading
            ) {
                Text("Anuluj")
            }
        }
    )

    if (showLocationPicker) {
        PlaceLocationPickerDialog(
            latitude = parsedLatitude!!,
            longitude = parsedLongitude!!,
            onDismiss = { showLocationPicker = false },
            onConfirm = { selectedLatitude, selectedLongitude ->
                latitude = selectedLatitude.toString()
                longitude = selectedLongitude.toString()
                showLocationPicker = false
            }
        )
    }
}

private fun formatVisitTime(value: String): String {
    val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    val date = try { input.parse(value.take(19)) } catch (_: java.text.ParseException) { null }
    return if (date == null) value else SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(date)
}

@Composable
private fun PlaceLocationPickerDialog(
    latitude: Double,
    longitude: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedLatitude by remember(latitude, longitude) { mutableStateOf(latitude) }
    var selectedLongitude by remember(latitude, longitude) { mutableStateOf(longitude) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                mapView?.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                mapView?.onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onPause()
            mapView = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ustaw położenie miejsca") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Przesuń znacznik w wybrane miejsce.",
                    style = MaterialTheme.typography.bodySmall
                )
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp, max = 420.dp),
                    factory = {
                        val cacheDirectory = File(context.cacheDir, "osmdroid")
                        Configuration.getInstance().apply {
                            userAgentValue = context.packageName
                            osmdroidBasePath = cacheDirectory
                            osmdroidTileCache = File(cacheDirectory, "tiles")
                        }

                        MapView(context).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(16.0)
                            controller.setCenter(GeoPoint(latitude, longitude))
                            val marker = Marker(this).apply {
                                position = GeoPoint(latitude, longitude)
                                title = "Nowe miejsce"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                isDraggable = true
                                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                    override fun onMarkerDrag(marker: Marker) = Unit

                                    override fun onMarkerDragEnd(marker: Marker) {
                                        selectedLatitude = marker.position.latitude
                                        selectedLongitude = marker.position.longitude
                                    }

                                    override fun onMarkerDragStart(marker: Marker) = Unit
                                })
                            }
                            overlays.add(marker)
                            onResume()
                            mapView = this
                        }
                    },
                    update = {}
                )
                Text(
                    text = "GPS: %.6f, %.6f".format(
                        java.util.Locale.US,
                        selectedLatitude,
                        selectedLongitude
                    ),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedLatitude, selectedLongitude) }) {
                Text("Zapisz położenie")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}
