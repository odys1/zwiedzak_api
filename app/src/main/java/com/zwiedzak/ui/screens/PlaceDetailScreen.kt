package com.zwiedzak.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.VisitStatus
import com.zwiedzak.ui.PlaceActionState
import com.zwiedzak.ui.PlaceDetailsState
import com.zwiedzak.ui.PlaceActionsViewModel
import java.io.File
import java.io.IOException
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailScreen(
    place: Place,
    placeActionsViewModel: PlaceActionsViewModel,
    isAuthenticated: Boolean,
    onAuthClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val actionState by placeActionsViewModel.state.collectAsState()
    val detailsState by placeActionsViewModel.detailsState.collectAsState()
    val categoriesState by placeActionsViewModel.categoriesState.collectAsState()
    val historyState by placeActionsViewModel.historyState.collectAsState()
    var activePlaceId by remember(place.id) { mutableStateOf(place.id) }
    val displayedPlace = (detailsState as? PlaceDetailsState.Success)?.place
        ?.takeIf { it.id == activePlaceId } ?: place
    val detailsLoaded = (detailsState as? PlaceDetailsState.Success)?.place?.id == activePlaceId
    var visitStatus by remember(place.id) { mutableStateOf(place.visitState) }
    var pendingVisitStatus by remember(place.id) { mutableStateOf<VisitStatus?>(null) }
    var showImageDialog by remember { mutableStateOf(false) }
    var imageUrl by remember { mutableStateOf("") }
    var imageCaption by remember { mutableStateOf("") }
    var selectedRating by remember { mutableStateOf(0) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditForm by remember(place.id) { mutableStateOf(true) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            placeActionsViewModel.clearState()
            placeActionsViewModel.uploadImage(context, displayedPlace.id, uri, imageCaption)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captured ->
        val cameraPath = pendingCameraPath
        if (captured && cameraPath != null) {
            placeActionsViewModel.clearState()
            val cameraUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(cameraPath)
            )
            placeActionsViewModel.uploadImage(
                context,
                displayedPlace.id,
                cameraUri,
                imageCaption
            )
        } else {
            cameraPath?.let { File(it).delete() }
            pendingCameraPath = null
        }
    }

    LaunchedEffect(activePlaceId) {
        placeActionsViewModel.clearState()
        placeActionsViewModel.loadPlaceDetails(activePlaceId)
        placeActionsViewModel.loadCategories()
    }

    LaunchedEffect(activePlaceId, isAuthenticated) {
        if (isAuthenticated) placeActionsViewModel.loadVisitHistory(activePlaceId)
    }

    LaunchedEffect(actionState) {
        val success = actionState as? PlaceActionState.Success
        if (success != null) {
            pendingVisitStatus?.let { visitStatus = it }
            pendingVisitStatus = null
            if (showImageDialog) {
                showImageDialog = false
                imageUrl = ""
                imageCaption = ""
            }
            if (showEditForm) showEditForm = false
            val resultId = success.placeId ?: activePlaceId
            if (resultId != activePlaceId) {
                Toast.makeText(context, success.message, Toast.LENGTH_LONG).show()
                activePlaceId = resultId
            } else {
                placeActionsViewModel.loadPlaceDetails(resultId)
                if (isAuthenticated) placeActionsViewModel.loadVisitHistory(resultId)
            }
        } else if (actionState is PlaceActionState.Error ||
            actionState is PlaceActionState.VisitUncertain
        ) {
            pendingVisitStatus = null
        }
        if (actionState !is PlaceActionState.Loading &&
            actionState !is PlaceActionState.Idle &&
            pendingCameraPath != null
        ) {
            pendingCameraPath?.let { File(it).delete() }
            pendingCameraPath = null
        }
    }

    LaunchedEffect(displayedPlace.visitState) {
        visitStatus = displayedPlace.visitState
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Szczegóły miejsca") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Powrót"
                        )
                    }
                },
                actions = {
                    if (detailsLoaded) {
                        TextButton(onClick = { placeActionsViewModel.clearState(); showEditForm = true }) {
                            Text("Formularz")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            displayedPlace.images.orEmpty().forEach { image ->
                AsyncImage(
                    model = image.imageUrl,
                    contentDescription = image.caption ?: "Zdjęcie: ${displayedPlace.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                image.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                    Text(caption, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (displayedPlace.images.isNullOrEmpty()) {
                displayedPlace.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Zdjęcie: ${displayedPlace.name}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
            if (detailsState is PlaceDetailsState.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (detailsState is PlaceDetailsState.Error) {
                Text(
                    (detailsState as PlaceDetailsState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayedPlace.name,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayedPlace.averageRating?.let { rating ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Ocena",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f", rating),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        displayedPlace.distanceKm?.let { dist ->
                            Text(
                                text = "• ${String.format(Locale.getDefault(), "%.1f", dist)} km stąd",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Category & Tags
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        displayedPlace.category?.let { cat ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(cat) }
                            )
                        }

                        if (displayedPlace.isFood) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("Jedzenie 🍔") }
                            )
                        }

                        if (displayedPlace.isLodging) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("Nocleg 🛏️") }
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Status wizyty", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = visitStatus.isVisited,
                            onCheckedChange = { checked ->
                                placeActionsViewModel.clearState()
                                val status = if (checked) VisitStatus.VISITED else VisitStatus.PLANNED
                                pendingVisitStatus = status
                                placeActionsViewModel.updateVisitStatus(displayedPlace.id, status.apiValue)
                            },
                            enabled = isAuthenticated && actionState !is PlaceActionState.Loading &&
                                detailsLoaded
                        )
                        Text("Oznacz jako odwiedzone")
                    }
                    if (isAuthenticated) {
                        if (visitStatus.isVisited) {
                            TextButton(
                                onClick = {
                                    placeActionsViewModel.clearState()
                                    pendingVisitStatus = VisitStatus.VISITED
                                    placeActionsViewModel.updateVisitStatus(
                                        displayedPlace.id,
                                        VisitStatus.VISITED.apiValue
                                    )
                                },
                                enabled = actionState !is PlaceActionState.Loading &&
                                    detailsLoaded
                            ) {
                                Text("Zapisz kolejną wizytę")
                            }
                        }
                        FilterChip(
                            selected = visitStatus == VisitStatus.AVOID,
                            onClick = {
                                if (visitStatus != VisitStatus.AVOID) {
                                    placeActionsViewModel.clearState()
                                    pendingVisitStatus = VisitStatus.AVOID
                                    placeActionsViewModel.updateVisitStatus(
                                        displayedPlace.id,
                                        VisitStatus.AVOID.apiValue
                                    )
                                }
                            },
                            enabled = actionState !is PlaceActionState.Loading &&
                                detailsLoaded,
                            label = { Text("Odwiedzone · nie wracam") }
                        )
                    } else {
                        Button(onClick = onAuthClick) {
                            Text("Zaloguj się, aby zapisać status")
                        }
                    }

                    Text("Oceń miejsce", style = MaterialTheme.typography.titleMedium)
                    if (!isAuthenticated) {
                        Button(onClick = onAuthClick) {
                            Text("Zaloguj się, aby dodać zdjęcia lub ocenę")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { score ->
                            TextButton(
                                onClick = { selectedRating = score },
                                enabled = isAuthenticated && actionState !is PlaceActionState.Loading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "$score gwiazdek",
                                    tint = if (score <= selectedRating) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                    if (isAuthenticated) {
                        Button(
                            onClick = {
                                placeActionsViewModel.clearState()
                                placeActionsViewModel.ratePlace(displayedPlace.id, selectedRating)
                            },
                            enabled = selectedRating in 1..5 &&
                                actionState !is PlaceActionState.Loading
                        ) {
                            Text("Zapisz ocenę")
                        }
                        IconButton(
                            onClick = {
                                placeActionsViewModel.clearState()
                                showImageDialog = true
                            },
                            enabled = actionState !is PlaceActionState.Loading
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Dodaj zdjęcie"
                            )
                        }
                    }

                    when (val state = actionState) {
                        is PlaceActionState.Loading -> CircularProgressIndicator()
                        is PlaceActionState.Success -> Text(
                            state.message,
                            color = MaterialTheme.colorScheme.primary
                        )
                        is PlaceActionState.Error -> Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        is PlaceActionState.VisitUncertain -> Unit
                        is PlaceActionState.EditConflict -> Unit
                        is PlaceActionState.EditUncertain -> Unit
                        PlaceActionState.Idle -> Unit
                    }
                }
            }

            // Address section
            displayedPlace.address?.let { address ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Adres",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Adres",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Description section
            displayedPlace.description?.let { desc ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Informacje",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "O miejscu",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Coordinates section if available
            if (displayedPlace.latitude != null && displayedPlace.longitude != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val coordinates = "${displayedPlace.latitude},${displayedPlace.longitude}"
                            val navigationIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:0,0?q=$coordinates(${Uri.encode(displayedPlace.name)})")
                            )
                            try {
                                context.startActivity(
                                    Intent.createChooser(navigationIntent, "Nawiguj do miejsca")
                                )
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    context,
                                    "Brak aplikacji obsługującej nawigację.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Współrzędne geograficzne",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Szerokość: ${displayedPlace.latitude}, Długość: ${displayedPlace.longitude}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Dotknij, aby wybrać aplikację nawigacyjną",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            }
        }
    }

    if (showEditForm && detailsLoaded) {
        AddPlaceDialog(
            initialLatitude = displayedPlace.latitude ?: 0.0,
            initialLongitude = displayedPlace.longitude ?: 0.0,
            actionState = actionState,
            categoriesState = categoriesState,
            editPlace = displayedPlace,
            isAuthenticated = isAuthenticated,
            historyState = historyState,
            onDismiss = { showEditForm = false; placeActionsViewModel.clearState() },
            onRetryCategories = placeActionsViewModel::loadCategories,
            onRetryHistory = { placeActionsViewModel.loadVisitHistory(activePlaceId) },
            onAuthClick = onAuthClick,
            onSubmit = { request, imageUri, caption, applyToShared ->
                placeActionsViewModel.editPlace(context, displayedPlace, request, imageUri, caption, applyToShared)
            }
        )
    }

    val uncertainVisit = actionState as? PlaceActionState.VisitUncertain
    if (uncertainVisit != null) {
        AlertDialog(
            onDismissRequest = placeActionsViewModel::clearState,
            title = { Text("Nieznany wynik zapisu wizyty") },
            text = {
                Text(
                    (if (uncertainVisit.status == VisitStatus.PLANNED.apiValue) {
                        "Serwer mógł zapisać zmianę statusu.\n\n"
                    } else {
                        "Serwer mógł zapisać wizytę. Ponowienie może dopisać ją drugi raz.\n\n"
                    }) +
                        uncertainVisit.historyMessage
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingVisitStatus = VisitStatus.fromApiValue(uncertainVisit.status)
                    placeActionsViewModel.updateVisitStatus(uncertainVisit.placeId, uncertainVisit.status)
                }) {
                    Text("Zapisz ponownie")
                }
            },
            dismissButton = {
                TextButton(onClick = placeActionsViewModel::clearState) {
                    Text("Nie ponawiaj")
                }
            }
        )
    }

    if (showImageDialog) {
        AlertDialog(
            onDismissRequest = {
                showImageDialog = false
                placeActionsViewModel.clearState()
            },
            title = { Text("Dodaj zdjęcie") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            enabled = actionState !is PlaceActionState.Loading
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
                                        "zwiedzak-camera-",
                                        ".jpg",
                                        context.cacheDir
                                    )
                                    val cameraUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        cameraFile
                                    )
                                    pendingCameraPath = cameraFile.absolutePath
                                    cameraLauncher.launch(cameraUri)
                                } catch (e: IOException) {
                                    cameraFile?.delete()
                                    pendingCameraPath = null
                                    Toast.makeText(
                                        context,
                                        "Nie udało się przygotować zdjęcia z aparatu.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: ActivityNotFoundException) {
                                    cameraFile?.delete()
                                    pendingCameraPath = null
                                    Toast.makeText(
                                        context,
                                        "Nie znaleziono aplikacji aparatu.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = actionState !is PlaceActionState.Loading
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Zrób zdjęcie aparatem"
                            )
                        }
                    }
                    Text("Lub dodaj zdjęcie przez adres HTTPS")
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Adres HTTPS zdjęcia") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = imageCaption,
                        onValueChange = { imageCaption = it.take(255) },
                        label = { Text("Podpis (opcjonalnie, do 255 znaków)") },
                        singleLine = true
                    )
                    when (val state = actionState) {
                        is PlaceActionState.Loading -> CircularProgressIndicator()
                        is PlaceActionState.Error -> Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Unit
                    }
                }
            },
            confirmButton = {
                IconButton(
                    enabled = imageUrl.trim().startsWith("https://", ignoreCase = true) &&
                        actionState !is PlaceActionState.Loading,
                    onClick = {
                        placeActionsViewModel.clearState()
                        placeActionsViewModel.addImage(
                            displayedPlace.id,
                            imageUrl.trim(),
                            imageCaption
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Dodaj zdjęcie z adresu"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImageDialog = false
                        placeActionsViewModel.clearState()
                    },
                    enabled = actionState !is PlaceActionState.Loading
                ) {
                    Text("Anuluj")
                }
            }
        )
    }
}
