package com.zwiedzak.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.VisitStatus
import com.zwiedzak.ui.PlaceActionState
import com.zwiedzak.ui.PlaceDetailsState
import com.zwiedzak.ui.PlaceActionsViewModel
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
    val displayedPlace = (detailsState as? PlaceDetailsState.Success)?.place
        ?.takeIf { it.id == place.id } ?: place
    var visitStatus by remember(place.id) { mutableStateOf(place.visitState) }
    var pendingVisitStatus by remember(place.id) { mutableStateOf<VisitStatus?>(null) }
    var showImageDialog by remember { mutableStateOf(false) }
    var imageUrl by remember { mutableStateOf("") }
    var imageCaption by remember { mutableStateOf("") }
    var selectedRating by remember { mutableStateOf(0) }

    LaunchedEffect(place.id) {
        placeActionsViewModel.clearState()
        placeActionsViewModel.loadPlaceDetails(place.id)
    }

    LaunchedEffect(actionState) {
        if (actionState is PlaceActionState.Success) {
            pendingVisitStatus?.let { visitStatus = it }
            pendingVisitStatus = null
            if (showImageDialog) {
                showImageDialog = false
                imageUrl = ""
                imageCaption = ""
            }
            placeActionsViewModel.loadPlaceDetails(place.id)
        } else if (actionState is PlaceActionState.Error) {
            pendingVisitStatus = null
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
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f", rating),
                                    color = Color.White,
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
                    if (isAuthenticated) {
                        VisitStatus.entries.forEach { status ->
                            FilterChip(
                                selected = visitStatus == status,
                                onClick = {
                                    if (visitStatus != status && actionState !is PlaceActionState.Loading) {
                                        placeActionsViewModel.clearState()
                                        pendingVisitStatus = status
                                        placeActionsViewModel.updateVisitStatus(displayedPlace.id, status.apiValue)
                                    }
                                },
                                enabled = actionState !is PlaceActionState.Loading,
                                label = { Text(status.label) }
                            )
                        }
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
                        OutlinedButton(
                            onClick = {
                                placeActionsViewModel.clearState()
                                showImageDialog = true
                            },
                            enabled = actionState !is PlaceActionState.Loading
                        ) {
                            Text("Dodaj zdjęcie przez adres HTTPS")
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

    if (showImageDialog) {
        AlertDialog(
            onDismissRequest = {
                showImageDialog = false
                placeActionsViewModel.clearState()
            },
            title = { Text("Dodaj zdjęcie") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Button(
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
                    Text("Dodaj")
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
