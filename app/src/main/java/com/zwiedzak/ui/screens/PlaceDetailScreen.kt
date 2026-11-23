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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val categoriesState by placeActionsViewModel.categoriesState.collectAsState()
    val historyState by placeActionsViewModel.historyState.collectAsState()
    var activePlaceId by remember(place.id) { mutableStateOf(place.id) }
    val displayedPlace = (detailsState as? PlaceDetailsState.Success)?.place
        ?.takeIf { it.id == activePlaceId } ?: place
    val detailsLoaded = (detailsState as? PlaceDetailsState.Success)?.place?.id == activePlaceId
    var showEditForm by remember(place.id) { mutableStateOf(false) }

    LaunchedEffect(activePlaceId, isAuthenticated) {
        placeActionsViewModel.clearState()
        placeActionsViewModel.loadPlaceDetails(activePlaceId)
        placeActionsViewModel.loadCategories()
    }

    LaunchedEffect(activePlaceId, isAuthenticated) {
        if (isAuthenticated) placeActionsViewModel.loadVisitHistory(activePlaceId)
        else showEditForm = false
    }

    LaunchedEffect(actionState) {
        val success = actionState as? PlaceActionState.Success
        if (success != null) {
            if (showEditForm) showEditForm = false
            val resultId = success.placeId ?: activePlaceId
            if (resultId != activePlaceId) {
                Toast.makeText(context, success.message, Toast.LENGTH_LONG).show()
                activePlaceId = resultId
            } else {
                placeActionsViewModel.loadPlaceDetails(resultId)
                if (isAuthenticated) placeActionsViewModel.loadVisitHistory(resultId)
            }
        }
    }

    if (showEditForm && detailsLoaded && isAuthenticated) {
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
            onRecordVisit = {
                placeActionsViewModel.clearState()
                placeActionsViewModel.updateVisitStatus(displayedPlace.id, displayedPlace.visitState.apiValue)
            },
            onRatePlace = { rating ->
                placeActionsViewModel.clearState()
                placeActionsViewModel.ratePlace(displayedPlace.id, rating)
            },
            onSubmit = { request, imageUri, caption ->
                placeActionsViewModel.editPlace(context, displayedPlace, request, imageUri, caption)
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
                    }) + uncertainVisit.historyMessage
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    placeActionsViewModel.updateVisitStatus(uncertainVisit.placeId, uncertainVisit.status)
                }) { Text("Zapisz ponownie") }
            },
            dismissButton = {
                TextButton(onClick = placeActionsViewModel::clearState) { Text("Nie ponawiaj") }
            }
        )
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
        PlaceDetailContent(
            displayedPlace = displayedPlace,
            isAuthenticated = isAuthenticated,
            detailsLoaded = detailsLoaded,
            detailsState = detailsState,
            actionState = actionState,
            onEdit = { placeActionsViewModel.clearState(); showEditForm = true },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
internal fun PlaceDetailContent(
    displayedPlace: Place,
    isAuthenticated: Boolean,
    detailsLoaded: Boolean,
    detailsState: PlaceDetailsState,
    actionState: PlaceActionState,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isAuthenticated) PlacePhotoGallery(displayedPlace)
        if (detailsState is PlaceDetailsState.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (detailsState is PlaceDetailsState.Error) {
            Text(
                (detailsState as PlaceDetailsState.Error).message,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (isAuthenticated) {
            Button(
                onClick = onEdit,
                enabled = detailsLoaded
            ) {
                Text("Edytuj miejsce")
            }
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

                displayedPlace.category?.let { category ->
                    Text("Kategoria: $category", style = MaterialTheme.typography.bodyMedium)
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
                    Text(displayedPlace.visitState.label)
                    Text(
                        "Ostatnia wizyta: " + (displayedPlace.lastVisitedAt?.let(::formatVisitTime)
                            ?: "Brak zapisanej wizyty")
                    )
                } else {
                    Text("Zaloguj się, aby zobaczyć swój status i ostatnią wizytę.")
                }
                Text("Nocleg: ${availabilityText(displayedPlace.hasAccommodation)}")
                Text("Jedzenie: ${availabilityText(displayedPlace.hasFood)}")
                (actionState as? PlaceActionState.Success)?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.primary)
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
                    text = displayedPlace.description?.takeIf { it.isNotBlank() } ?: "Brak opisu",
                    style = MaterialTheme.typography.bodyMedium
                )
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
        } else {
            Text("Współrzędne geograficzne: Brak danych")
        }
    }
}

private fun availabilityText(value: Int?): String = when (value) {
    1 -> "Tak"
    0 -> "Nie"
    else -> "Brak danych"
}
