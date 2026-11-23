package com.zwiedzak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.zwiedzak.data.auth.AuthTokenManager
import com.zwiedzak.data.network.RetrofitInstance
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.preferences.MapPreferences
import com.zwiedzak.ui.NearbyPlacesViewModel
import com.zwiedzak.ui.NearbyUiState
import com.zwiedzak.ui.PlaceActionsViewModel
import com.zwiedzak.ui.auth.AuthState
import com.zwiedzak.ui.auth.AuthViewModel
import com.zwiedzak.ui.screens.AuthScreen
import com.zwiedzak.ui.screens.MapPlacesScreen
import com.zwiedzak.ui.screens.NearbyPlacesScreen
import com.zwiedzak.ui.screens.PlaceDetailScreen
import com.zwiedzak.ui.theme.ZwiedzakTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NearbyPlacesViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val placeActionsViewModel: PlaceActionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RetrofitInstance.init(applicationContext)
        setContent {
            ZwiedzakTheme {
                ZwiedzakApp(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    placeActionsViewModel = placeActionsViewModel,
                    initialRadiusKm = MapPreferences.getRadiusKm(applicationContext)
                )
            }
        }
    }
}

@Composable
fun ZwiedzakApp(
    viewModel: NearbyPlacesViewModel,
    authViewModel: AuthViewModel,
    placeActionsViewModel: PlaceActionsViewModel,
    initialRadiusKm: Int
) {
    val context = LocalContext.current
    var showAuth by rememberSaveable { mutableStateOf(false) }
    var showMap by rememberSaveable { mutableStateOf(false) }
    var searchMapPlaces by remember { mutableStateOf<List<Place>?>(null) }
    var radiusKm by rememberSaveable {
        mutableIntStateOf(initialRadiusKm)
    }
    var mapCenterLatitude by rememberSaveable { mutableStateOf(52.23) }
    var mapCenterLongitude by rememberSaveable { mutableStateOf(21.01) }
    var hasSelectedMapCenter by rememberSaveable { mutableStateOf(false) }
    var mapUserLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var mapUserLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    val selectedPlace by viewModel.selectedPlace.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val mapUiState by viewModel.mapUiState.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val tokenManager = remember(context) { AuthTokenManager.getInstance(context) }
    val screenStateHolder = rememberSaveableStateHolder()
    val sessionExpired by tokenManager.sessionExpired.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.checkAuthStatus(context)
    }
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) authViewModel.onSessionExpired(context)
    }
    LaunchedEffect(authState) {
        if (authState is AuthState.Unauthenticated) {
            if (selectedPlace != null) viewModel.selectPlace(null)
            searchMapPlaces = null
            if (showMap) {
                viewModel.clearSessionPlaces()
                viewModel.fetchMapPlaces(mapCenterLatitude, mapCenterLongitude, radiusKm * 1000)
            }
        }
    }

    if (sessionExpired) {
        AlertDialog(
            onDismissRequest = tokenManager::acknowledgeExpiration,
            title = { Text("Sesja zakończona") },
            text = { Text("Token wygasł lub jest nieprawidłowy. Zostałeś automatycznie wylogowany. Zaloguj się ponownie.") },
            confirmButton = {
                TextButton(onClick = tokenManager::acknowledgeExpiration) {
                    Text("OK")
                }
            }
        )
    }

    LaunchedEffect(uiState) {
        val success = uiState as? NearbyUiState.Success
        if (!hasSelectedMapCenter && success != null) {
            mapCenterLatitude = success.userLat
            mapCenterLongitude = success.userLng
        }
    }
    LaunchedEffect(showMap, searchMapPlaces, mapCenterLatitude, mapCenterLongitude, radiusKm) {
        if (showMap && searchMapPlaces == null) {
            viewModel.fetchMapPlaces(
                mapCenterLatitude,
                mapCenterLongitude,
                radiusKm * 1000
            )
        }
    }

    val backFromPlaceDetails: () -> Unit = {
        viewModel.selectPlace(null)
        val current = uiState as? NearbyUiState.Success
        if (current != null) {
            viewModel.retryApiFetch(current.userLat, current.userLng, radiusKm * 1000)
        }
    }

    BackHandler(enabled = showAuth || selectedPlace != null || showMap) {
        if (showAuth) {
            showAuth = false
        } else if (selectedPlace != null) {
            backFromPlaceDetails()
        } else if (showMap) {
            showMap = false
            searchMapPlaces = null
        }
    }

    when {
        showAuth -> AuthScreen(
            authViewModel = authViewModel,
            onBackClick = { showAuth = false }
        )
        selectedPlace != null -> PlaceDetailScreen(
            place = selectedPlace!!,
            placeActionsViewModel = placeActionsViewModel,
            isAuthenticated = authState is AuthState.Authenticated,
            onAuthClick = { showAuth = true },
            onBackClick = backFromPlaceDetails
        )
        showMap -> {
            val success = uiState as? NearbyUiState.Success
            val mapSuccess = mapUiState as? NearbyUiState.Success
            val mapError = mapUiState as? NearbyUiState.Error
            val displayedPlaces = if (mapError != null) null else mapSuccess ?: success
            MapPlacesScreen(
                places = searchMapPlaces ?: displayedPlaces?.places.orEmpty(),
                isMockData = displayedPlaces?.isMockData == true,
                apiErrorMessage = if (searchMapPlaces != null) null else mapError?.message ?: displayedPlaces?.apiErrorMessage,
                showSearchResults = searchMapPlaces != null,
                centerLatitude = mapCenterLatitude,
                centerLongitude = mapCenterLongitude,
                userLatitude = mapUserLatitude ?: success?.userLat,
                userLongitude = mapUserLongitude ?: success?.userLng,
                radiusKm = radiusKm,
                onRadiusChange = { newRadiusKm ->
                    radiusKm = newRadiusKm
                    MapPreferences.setRadiusKm(context, newRadiusKm)
                    viewModel.retryApiFetch(
                        success?.userLat ?: mapCenterLatitude,
                        success?.userLng ?: mapCenterLongitude,
                        newRadiusKm * 1000
                    )
                },
                onCenterChange = { latitude, longitude ->
                    hasSelectedMapCenter = true
                    mapCenterLatitude = latitude
                    mapCenterLongitude = longitude
                },
                onUserLocationChange = { latitude, longitude ->
                    mapUserLatitude = latitude
                    mapUserLongitude = longitude
                },
                onPlaceClick = viewModel::selectPlace,
                onBackClick = {
                    showMap = false
                    searchMapPlaces = null
                }
            )
        }
        else -> screenStateHolder.SaveableStateProvider("nearby") {
            NearbyPlacesScreen(
                viewModel = viewModel,
                authViewModel = authViewModel,
                placeActionsViewModel = placeActionsViewModel,
                radiusKm = radiusKm,
                onRadiusChange = { newRadiusKm ->
                    radiusKm = newRadiusKm
                    MapPreferences.setRadiusKm(context, newRadiusKm)
                },
                onPlaceClick = viewModel::selectPlace,
                onAuthClick = { showAuth = true },
                onMapClick = {
                    searchMapPlaces = null
                    showMap = true
                },
                onShowSearchResultsMap = { places ->
                    searchMapPlaces = places
                    showMap = true
                }
            )
        }
    }
}
