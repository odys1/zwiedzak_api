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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.zwiedzak.data.model.CreatePlaceRequest
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.VisitStatus
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
    onSubmit: (CreatePlaceRequest, Uri?, String) -> Unit,
    editPlace: Place? = null,
    isAuthenticated: Boolean = true,
    historyState: VisitHistoryState = VisitHistoryState.Loading,
    onRetryHistory: () -> Unit = {},
    onAuthClick: () -> Unit = {},
    onRecordVisit: (() -> Unit)? = null,
    onRatePlace: ((Int) -> Unit)? = null
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
    var visitStatus by remember { mutableStateOf(editPlace?.visitState ?: VisitStatus.PLANNED) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageCaption by remember { mutableStateOf("") }
    var cameraImagePath by remember { mutableStateOf<String?>(null) }
    var historyExpanded by remember { mutableStateOf(false) }
    var selectedVisit by remember { mutableStateOf("") }
    var showPhotoConfirmation by remember { mutableStateOf(false) }
    var showDuplicateConfirmation by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<EditExtraAction?>(null) }
    var selectedRating by remember { mutableStateOf(0) }
    val currentCameraPath by rememberUpdatedState(cameraImagePath)

    DisposableEffect(Unit) {
        onDispose { currentCameraPath?.let { File(it).delete() } }
    }

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
        actionState !is PlaceActionState.Loading && actionState !is PlaceActionState.CreatedWithWarning

    val formRequest = CreatePlaceRequest(
        name = name.trim(),
        category = selectedCategory?.code ?: categoryCode,
        latitude = parsedLatitude ?: 0.0,
        longitude = parsedLongitude ?: 0.0,
        address = address.trim().takeIf { it.isNotEmpty() },
        description = description.trim().takeIf { it.isNotEmpty() },
        hasAccommodation = hasAccommodation,
        hasFood = hasFood,
        visibility = "public",
        confirmDistinct = editPlace != null && actionState is PlaceActionState.EditConflict,
        visited = visitStatus.isVisited,
        editVisitStatus = if (editPlace != null) visitStatus else null,
        createVisitStatus = if (editPlace == null && visitStatus == VisitStatus.AVOID) visitStatus.apiValue else null
    )
    val conflict = actionState as? PlaceActionState.CreateConflict
    val matchingConflict = editPlace == null && conflict?.request == formRequest
    val submit: (Boolean) -> Unit = { confirmDistinct ->
        onSubmit(formRequest.copy(confirmDistinct = formRequest.confirmDistinct || confirmDistinct), selectedImageUri, imageCaption)
    }

    AlertDialog(
        onDismissRequest = {
            if (actionState !is PlaceActionState.Loading) {
                cameraImagePath?.let { File(it).delete() }
                onDismiss()
            }
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
                if (isAuthenticated && editPlace != null) PlacePhotoGallery(editPlace)
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
                Text(
                    if (editPlace == null) "Nowe miejsce będzie widoczne dla wszystkich. Zdjęcie będzie prywatne."
                    else "Zmiany danych i zdjęcia są widoczne tylko dla Ciebie.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (editPlace == null) {
                    Column {
                        VisitStatusCheckboxes(
                            status = visitStatus,
                            showAvoid = true,
                            enabled = isAuthenticated && actionState !is PlaceActionState.Loading,
                            onChange = { visitStatus = it }
                        )
                        val enabled = isAuthenticated && actionState !is PlaceActionState.Loading
                        NewPlaceCheckbox("Jest hotel / nocleg", hasAccommodation, enabled) { hasAccommodation = it }
                        NewPlaceCheckbox("Jest restauracja / jedzenie", hasFood, enabled) { hasFood = it }
                    }
                } else {
                    VisitStatusCheckboxes(
                        status = visitStatus,
                        showAvoid = true,
                        enabled = isAuthenticated && actionState !is PlaceActionState.Loading,
                        onChange = { visitStatus = it }
                    )
                }
                if (editPlace != null && isAuthenticated) {
                    Text(
                        "Ostatnia wizyta: " + (editPlace.lastVisitedAt?.let(::formatVisitTime)
                            ?: "Brak zapisanej wizyty"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Zmiana na odwiedzone zapisze wizytę.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (editPlace.visitState.isVisited && onRecordVisit != null) {
                        TextButton(
                            onClick = { pendingAction = EditExtraAction.RECORD_VISIT },
                            enabled = actionState !is PlaceActionState.Loading
                        ) { Text("Zapisz kolejną wizytę") }
                    }
                    if (onRatePlace != null) {
                        Text("Oceń miejsce", style = MaterialTheme.typography.titleSmall)
                        Row {
                            (1..5).forEach { score ->
                                IconButton(
                                    onClick = { selectedRating = score },
                                    enabled = actionState !is PlaceActionState.Loading
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "$score gwiazdek",
                                        tint = if (score <= selectedRating) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { pendingAction = EditExtraAction.RATE },
                            enabled = selectedRating in 1..5 && actionState !is PlaceActionState.Loading
                        ) { Text("Zapisz ocenę") }
                    }
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
                if (editPlace != null) {
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
                    is PlaceActionState.CreateConflict -> Text(
                        if (matchingConflict) "Znaleziono podobne miejsca. Sprawdź je przed dodaniem."
                        else "Dane zostały zmienione. Sprawdź ponownie podobne miejsca.",
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
                    if (editPlace != null && selectedImageUri != null) {
                        showPhotoConfirmation = true
                    } else if (matchingConflict) {
                        showDuplicateConfirmation = true
                    } else {
                        submit(false)
                    }
                }
            ) {
                Text(when {
                    actionState is PlaceActionState.EditConflict -> "Potwierdź i zapisz"
                    actionState is PlaceActionState.EditUncertain -> "Zapisz ponownie"
                    matchingConflict -> "Sprawdź podobne"
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

    if (showPhotoConfirmation && isAuthenticated) {
        ConfirmAddPhotoDialog(
            onConfirm = {
                showPhotoConfirmation = false
                submit(false)
            },
            onDismiss = { showPhotoConfirmation = false }
        )
    }

    if (showDuplicateConfirmation && matchingConflict) {
        AlertDialog(
            onDismissRequest = { showDuplicateConfirmation = false },
            title = { Text("Podobne miejsca") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sprawdź, czy miejsce już istnieje. Czy na pewno dodajesz inne miejsce?")
                    conflict.candidates.forEach { candidate ->
                        val distance = candidate.distanceMeters?.let { " ($it m)" }.orEmpty()
                        val address = candidate.address?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
                        Text("${candidate.name}$distance$address")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDuplicateConfirmation = false; submit(true) }) {
                    Text("To inne miejsce")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateConfirmation = false }) { Text("Wróć") }
            }
        )
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (action == EditExtraAction.RECORD_VISIT) "Zapisz kolejną wizytę" else "Zapisz ocenę") },
            text = { Text("Niezapisane zmiany w formularzu nie zostaną zapisane. Czy chcesz kontynuować?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    if (action == EditExtraAction.RECORD_VISIT) onRecordVisit?.invoke()
                    else onRatePlace?.invoke(selectedRating)
                }) { Text("Kontynuuj") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Anuluj") } }
        )
    }

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

private enum class EditExtraAction { RECORD_VISIT, RATE }

@Composable
private fun NewPlaceCheckbox(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, maxLines = 1, softWrap = false)
    }
}

internal fun formatVisitTime(value: String): String {
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
