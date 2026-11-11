package com.zwiedzak.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.VisitStatus
import com.zwiedzak.data.network.RetrofitInstance
import com.zwiedzak.data.preferences.MapPreferences
import com.zwiedzak.ui.NearbyPlacesViewModel
import com.zwiedzak.ui.NearbyUiState
import com.zwiedzak.ui.PlaceActionState
import com.zwiedzak.ui.PlaceActionsViewModel
import com.zwiedzak.ui.auth.AuthState
import com.zwiedzak.ui.auth.AuthViewModel
import java.util.Locale
import java.text.Collator

private enum class VisitFilter {
    ALL,
    VISITED,
    NOT_VISITED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyPlacesScreen(
    viewModel: NearbyPlacesViewModel,
    authViewModel: AuthViewModel,
    placeActionsViewModel: PlaceActionsViewModel,
    radiusKm: Int,
    onRadiusChange: (Int) -> Unit,
    onPlaceClick: (Place) -> Unit,
    onAuthClick: () -> Unit,
    onMapClick: () -> Unit,
    onShowSearchResultsMap: (List<Place>) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val searchUiState by viewModel.searchUiState.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val placeActionState by placeActionsViewModel.state.collectAsState()

    var showServerDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var nameQuery by rememberSaveable { mutableStateOf("") }
    var addressQuery by rememberSaveable { mutableStateOf("") }
    var visitFilter by rememberSaveable { mutableStateOf(VisitFilter.ALL) }
    var filterFood by rememberSaveable { mutableStateOf(false) }
    var filterLodging by rememberSaveable { mutableStateOf(false) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var hasAppliedSearch by rememberSaveable { mutableStateOf(false) }
    var appliedNameQuery by rememberSaveable { mutableStateOf("") }
    var appliedAddressQuery by rememberSaveable { mutableStateOf("") }
    var appliedVisitFilter by rememberSaveable { mutableStateOf(VisitFilter.ALL) }
    var appliedFilterFood by rememberSaveable { mutableStateOf(false) }
    var appliedFilterLodging by rememberSaveable { mutableStateOf(false) }
    val isAuthenticated = authState is AuthState.Authenticated
    val appliedVisitedQuery = when {
        !isAuthenticated -> null
        appliedVisitFilter == VisitFilter.VISITED -> true
        appliedVisitFilter == VisitFilter.NOT_VISITED -> false
        else -> null
    }
    val isSearchActive = hasAppliedSearch
    val displayedUiState = if (isSearchActive) {
        when (val searchState = searchUiState) {
            is NearbyUiState.Idle -> NearbyUiState.Loading
            else -> searchState
        }
    } else {
        uiState
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.onPermissionGranted(context, radiusKm * 1000)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchLocationAndPlaces(context, radiusKm * 1000)
        authViewModel.checkAuthStatus(context)
    }

    LaunchedEffect(placeActionState, showAddPlaceDialog) {
        if (showAddPlaceDialog && placeActionState is PlaceActionState.Success) {
            showAddPlaceDialog = false
            placeActionsViewModel.clearState()
            viewModel.fetchLocationAndPlaces(context, radiusKm * 1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Miejsca w pobliżu") },
                actions = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            viewModel.searchPlaces(
                                appliedNameQuery,
                                appliedAddressQuery,
                                appliedVisitedQuery
                            )
                        } else {
                            viewModel.fetchLocationAndPlaces(context, radiusKm * 1000)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Odśwież"
                        )
                    }
                    IconButton(onClick = onMapClick) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Pokaż mapę"
                        )
                    }
                    IconButton(onClick = {
                        if (authState is AuthState.Authenticated) {
                            showProfileDialog = true
                        } else {
                            onAuthClick()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Konto",
                            tint = if (authState is AuthState.Authenticated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showServerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ustawienia API"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isAuthenticated) {
                        placeActionsViewModel.clearState()
                        showAddPlaceDialog = true
                    } else {
                        onAuthClick()
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Dodaj miejsce")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = displayedUiState) {
                is NearbyUiState.Idle, is NearbyUiState.PermissionRequired -> {
                    PermissionRequiredView(
                        onRequestPermission = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }

                is NearbyUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isSearchActive) {
                                "Wyszukiwanie miejsc..."
                            } else {
                                "Pobieranie lokalizacji i miejsc w pobliżu..."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is NearbyUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Wystąpił błąd",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            if (isSearchActive) {
                                viewModel.searchPlaces(
                                    appliedNameQuery,
                                    appliedAddressQuery,
                                    appliedVisitedQuery
                                )
                            } else {
                                viewModel.fetchLocationAndPlaces(context, radiusKm * 1000)
                            }
                        }) {
                            Text("Spróbuj ponownie")
                        }
                    }
                }

                is NearbyUiState.Success -> {
                    val polishCollator = remember {
                        Collator.getInstance(Locale.forLanguageTag("pl-PL"))
                    }
                    val filteredPlaces = remember(
                        state.places,
                        nameQuery,
                        addressQuery,
                        appliedNameQuery,
                        appliedAddressQuery,
                        appliedVisitFilter,
                        isSearchActive,
                        isAuthenticated,
                        appliedFilterFood,
                        appliedFilterLodging,
                        sortAscending
                    ) {
                        val matchingPlaces = state.places.filter { place ->
                            val matchesText = isSearchActive ||
                                (place.matchesNameQuery(appliedNameQuery) &&
                                    place.matchesAddressQuery(appliedAddressQuery))
                            val matchesVisit = (isSearchActive && isAuthenticated) ||
                                !isAuthenticated ||
                                when (appliedVisitFilter) {
                                    VisitFilter.ALL -> true
                                    VisitFilter.VISITED -> place.visitState != VisitStatus.PLANNED
                                    VisitFilter.NOT_VISITED -> place.visitState == VisitStatus.PLANNED
                                }
                            matchesText &&
                                matchesVisit &&
                                (!appliedFilterFood || place.isFood) &&
                                (!appliedFilterLodging || place.isLodging)
                        }

                        matchingPlaces.sortedWith { first, second ->
                            val nameComparison = polishCollator.compare(first.name, second.name)
                                .let { if (sortAscending) it else -it }
                            if (nameComparison == 0) first.id.compareTo(second.id) else nameComparison
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // User Status Bar if logged in
                        if (authState is AuthState.Authenticated) {
                            val userEmail = (authState as AuthState.Authenticated).email
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Zalogowano jako: $userEmail",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    TextButton(onClick = { showProfileDialog = true }) {
                                        Text("Moje konto")
                                    }
                                }
                            }
                        }

                        // Mock data notice if server unreachable
                        if (state.isMockData) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Brak aktywnego serwera API (${RetrofitInstance.getBaseUrl()}) – wyświetlam dane demonstracyjne.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = nameQuery,
                            onValueChange = { nameQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            label = { Text("Szukaj po nazwie") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Szukaj po nazwie")
                            },
                            trailingIcon = {
                                if (nameQuery.isNotEmpty()) {
                                    IconButton(onClick = { nameQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Wyczyść nazwę"
                                        )
                                    }
                                }
                            },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = addressQuery,
                            onValueChange = { addressQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            label = { Text("Szukaj po adresie, mieście lub województwie") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Szukaj po adresie"
                                )
                            },
                            trailingIcon = {
                                if (addressQuery.isNotEmpty()) {
                                    IconButton(onClick = { addressQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Wyczyść adres"
                                        )
                                    }
                                }
                            },
                            singleLine = true
                        )

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = visitFilter == VisitFilter.ALL,
                                    onClick = { visitFilter = VisitFilter.ALL },
                                    label = { Text("Wszystkie") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = visitFilter == VisitFilter.VISITED,
                                    onClick = {
                                        if (!isAuthenticated) {
                                            onAuthClick()
                                        } else {
                                            visitFilter = if (visitFilter == VisitFilter.VISITED) {
                                                VisitFilter.ALL
                                            } else {
                                                VisitFilter.VISITED
                                            }
                                        }
                                    },
                                    label = { Text("Odwiedzone") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = visitFilter == VisitFilter.NOT_VISITED,
                                    onClick = {
                                        if (!isAuthenticated) {
                                            onAuthClick()
                                        } else {
                                            visitFilter = if (visitFilter == VisitFilter.NOT_VISITED) {
                                                VisitFilter.ALL
                                            } else {
                                                VisitFilter.NOT_VISITED
                                            }
                                        }
                                    },
                                    label = { Text("Nieodwiedzone") }
                                )
                            }
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = filterLodging,
                                    onClick = { filterLodging = !filterLodging },
                                    label = { Text("Hotel") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filterFood,
                                    onClick = { filterFood = !filterFood },
                                    label = { Text("Restauracja") }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                appliedNameQuery = nameQuery.trim()
                                appliedAddressQuery = addressQuery.trim()
                                appliedVisitFilter = visitFilter
                                appliedFilterFood = filterFood
                                appliedFilterLodging = filterLodging
                                hasAppliedSearch = true
                                viewModel.searchPlaces(
                                    name = nameQuery.trim(),
                                    address = addressQuery.trim(),
                                    visited = when {
                                        !isAuthenticated -> null
                                        visitFilter == VisitFilter.VISITED -> true
                                        visitFilter == VisitFilter.NOT_VISITED -> false
                                        else -> null
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text("Pokaż")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Wyniki: ${filteredPlaces.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasAppliedSearch && filteredPlaces.isNotEmpty()) {
                                TextButton(
                                    onClick = { onShowSearchResultsMap(filteredPlaces) }
                                ) {
                                    Text("Pokaż na mapie")
                                }
                            }
                            TextButton(onClick = { sortAscending = !sortAscending }) {
                                Text(
                                    if (sortAscending) {
                                        "Nazwa A–Z"
                                    } else {
                                        "Nazwa Z–A"
                                    }
                                )
                            }
                        }

                        if (filteredPlaces.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (state.places.isEmpty() && !isSearchActive) {
                                        "Serwer API nie zwrócił miejsc w tej okolicy."
                                    } else {
                                        "Brak miejsc spełniających kryteria."
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredPlaces, key = { it.id }) { place ->
                                    PlaceCard(
                                        place = place,
                                        onClick = { onPlaceClick(place) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog && authState is AuthState.Authenticated) {
        val email = (authState as AuthState.Authenticated).email
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Konto użytkownika") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zalogowany e-mail:", style = MaterialTheme.typography.labelMedium)
                    Text(email, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("Twoje konto umożliwia ocenianie miejsc, dodawanie zdjęć oraz zapisywanie do własnej listy.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.logout(context)
                        showProfileDialog = false
                    }
                ) {
                    Text("Wyloguj się", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Zamknij")
                }
            }
        )
    }

    if (showServerDialog) {
        ServerUrlDialog(
            currentUrl = RetrofitInstance.getBaseUrl(),
            radiusKm = radiusKm,
            onDismiss = { showServerDialog = false },
            onSave = { newUrl, newRadiusKm ->
                RetrofitInstance.setBaseUrl(newUrl)
                onRadiusChange(newRadiusKm)
                showServerDialog = false
                viewModel.fetchLocationAndPlaces(context, newRadiusKm * 1000)
            }
        )
    }

    if (showAddPlaceDialog) {
        val location = uiState as? NearbyUiState.Success
        AddPlaceDialog(
            initialLatitude = location?.userLat ?: 52.23,
            initialLongitude = location?.userLng ?: 21.01,
            actionState = placeActionState,
            onDismiss = {
                showAddPlaceDialog = false
                placeActionsViewModel.clearState()
            },
            onSubmit = placeActionsViewModel::createPlace
        )
    }
}

@Composable
fun PlaceCard(
    place: Place,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageUrl = place.imageUrl
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { url ->
                    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://")) {
                        url
                    } else {
                        "${RetrofitInstance.getBaseUrl().trimEnd('/')}/${url.trimStart('/')}"
                    }
                }

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Zdjęcie: ${place.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                VisitStatusIndicator(
                    status = place.visitState,
                    markerColor = place.markerColor
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    place.averageRating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Ocena",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f", rating),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    place.distanceKm?.let { dist ->
                        Text(
                            text = "${String.format(Locale.getDefault(), "%.1f", dist)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Category & Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    place.category?.let { cat ->
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    if (place.isFood) {
                        Text(
                            text = "• Jedzenie",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    if (place.isLodging) {
                        Text(
                            text = "• Nocleg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Szczegóły",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun VisitStatusIndicator(
    status: VisitStatus,
    markerColor: String?
) {
    val dotColor = when (markerColor?.trim()?.lowercase(Locale.ROOT)) {
        "green", "#4caf50", "#2e7d32" -> Color(0xFF2E7D32)
        "gray", "grey", "#9e9e9e", "#757575" -> Color(0xFF757575)
        "red", "#f44336", "#c62828" -> Color(0xFFC62828)
        else -> when (status) {
            VisitStatus.PLANNED -> Color(0xFF757575)
            VisitStatus.VISITED -> Color(0xFF2E7D32)
            VisitStatus.AVOID -> Color(0xFFC62828)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PermissionRequiredView(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Wymagany dostęp do lokalizacji",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Aby wyświetlić restauracje, hotele i inne atrakcje w Twojej okolicy, aplikacja potrzebuje dostępu do lokalizacji GPS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Zezwól na dostęp do lokalizacji")
        }
    }
}

@Composable
fun ServerUrlDialog(
    currentUrl: String,
    radiusKm: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    var selectedRadiusKm by remember(radiusKm) { mutableIntStateOf(radiusKm) }
    var showRadiusMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konfiguracja adresu API") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Domyślny adres API: http://miejsca.webd.pro/zwiedzak/\nWpisz adres bazowy serwera, bez końcówki api/v1. Promień określa odległość od środka do granicy okręgu.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Adres serwera API") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    TextButton(onClick = { showRadiusMenu = true }) {
                        Text("Promień wyszukiwania: $selectedRadiusKm km")
                    }
                    DropdownMenu(
                        expanded = showRadiusMenu,
                        onDismissRequest = { showRadiusMenu = false }
                    ) {
                        MapPreferences.availableRadiiKm.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (option == selectedRadiusKm) {
                                            "$option km · wybrano"
                                        } else {
                                            "$option km"
                                        }
                                    )
                                },
                                onClick = {
                                    selectedRadiusKm = option
                                    showRadiusMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(urlText, selectedRadiusKm) }) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}
