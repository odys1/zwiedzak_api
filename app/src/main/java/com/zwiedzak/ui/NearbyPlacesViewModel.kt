package com.zwiedzak.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.network.ApiService
import com.zwiedzak.data.network.RetrofitInstance
import com.zwiedzak.location.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class NearbyPlacesViewModel : ViewModel() {

    private var mapFetchJob: Job? = null
    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow<NearbyUiState>(NearbyUiState.Idle)
    val uiState: StateFlow<NearbyUiState> = _uiState.asStateFlow()

    private val _searchUiState = MutableStateFlow<NearbyUiState>(NearbyUiState.Idle)
    val searchUiState: StateFlow<NearbyUiState> = _searchUiState.asStateFlow()

    private val _selectedPlace = MutableStateFlow<Place?>(null)
    val selectedPlace: StateFlow<Place?> = _selectedPlace.asStateFlow()

    private val _mapUiState = MutableStateFlow<NearbyUiState>(NearbyUiState.Idle)
    val mapUiState: StateFlow<NearbyUiState> = _mapUiState.asStateFlow()

    fun selectPlace(place: Place?) {
        _selectedPlace.value = place
    }

    fun searchPlaces(context: Context, name: String, address: String, visited: Boolean?, radiusMeters: Int, mine: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchUiState.value = NearbyUiState.Loading
            delay(350)
            try {
                val currentLocation = _uiState.value as? NearbyUiState.Success
                _searchUiState.value = searchPlacesFromApi(
                    api = RetrofitInstance.api,
                    name = name,
                    address = address,
                    visited = visited,
                    mine = mine,
                    radiusMeters = radiusMeters,
                    fallbackLocation = currentLocation?.let { it.userLat to it.userLng },
                    currentLocation = {
                        val tracker = LocationTracker(context.applicationContext)
                        if (!tracker.hasLocationPermission()) throw SearchLocationPermissionRequired()
                        val location = tracker.getCurrentLocation()
                            ?: throw IllegalStateException("Nie udało się ustalić lokalizacji. Włącz lokalizację i spróbuj ponownie.")
                        location.latitude to location.longitude
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchLocationPermissionRequired) {
                _searchUiState.value = NearbyUiState.PermissionRequired
            } catch (e: HttpException) {
                val message = if (name.isBlank() && address.isBlank() && visited == null) {
                    nearbyErrorMessage(e, radiusMeters)
                } else {
                    e.localizedMessage ?: "Nie udało się wyszukać miejsc."
                }
                _searchUiState.value = NearbyUiState.Error(message)
            } catch (e: Exception) {
                _searchUiState.value = NearbyUiState.Error(
                    e.localizedMessage ?: "Nie udało się wyszukać miejsc."
                )
            }

        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchUiState.value = NearbyUiState.Idle
    }

    fun onPermissionGranted(context: Context, radiusMeters: Int = 10000) {
        fetchLocationAndPlaces(context, radiusMeters)
    }

    fun fetchLocationAndPlaces(context: Context, radiusMeters: Int = 10000) {
        val locationTracker = LocationTracker(context)

        if (!locationTracker.hasLocationPermission()) {
            _uiState.value = NearbyUiState.PermissionRequired
            return
        }

        viewModelScope.launch {
            _uiState.value = NearbyUiState.Loading
            val location = locationTracker.getCurrentLocation()

            val lat = location?.latitude ?: 52.23
            val lng = location?.longitude ?: 21.01

            _uiState.value = loadPlacesFromApi(lat, lng, radiusMeters)
        }
    }

    private suspend fun loadPlacesFromApi(
        lat: Double,
        lng: Double,
        radiusMeters: Int = 10000
    ): NearbyUiState {
        return try {
            val response = RetrofitInstance.api.getNearbyPlaces(
                latitude = lat,
                longitude = lng,
                radiusMeters = radiusMeters
            )
            NearbyUiState.Success(
                places = response.items,
                userLat = lat,
                userLng = lng,
                isMockData = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 400 && radiusMeters > 50_000) {
                NearbyUiState.Error(nearbyErrorMessage(e, radiusMeters))
            } else {
                NearbyUiState.Success(
                    places = getMockPlaces(),
                    userLat = lat,
                    userLng = lng,
                    isMockData = true,
                    apiErrorMessage = e.localizedMessage ?: e.javaClass.simpleName
                )
            }
        } catch (e: Exception) {
            NearbyUiState.Success(
                places = getMockPlaces(),
                userLat = lat,
                userLng = lng,
                isMockData = true,
                apiErrorMessage = e.localizedMessage ?: e.javaClass.simpleName
            )
        }
    }

    fun retryApiFetch(lat: Double, lng: Double, radiusMeters: Int = 10000) {
        viewModelScope.launch {
            _uiState.value = NearbyUiState.Loading
            _uiState.value = loadPlacesFromApi(lat, lng, radiusMeters)
        }
    }

    fun fetchMapPlaces(lat: Double, lng: Double, radiusMeters: Int) {
        mapFetchJob?.cancel()
        mapFetchJob = viewModelScope.launch {
            _mapUiState.value = NearbyUiState.Loading
            _mapUiState.value = loadPlacesFromApi(lat, lng, radiusMeters)
        }
    }

    private fun getMockPlaces(): List<Place> {
        return listOf(
            Place(
                id = "1",
                name = "Restauracja Staromiejska",
                description = "Tradycyjna kuchnia polska w sercu starego miasta. Wyśmienite pierogi i dania obiadowe.",
                address = "ul. Rynek 12, Warszawa",
                category = "Gastronomia",
                latitude = 52.2497,
                longitude = 21.0122,
                hasAccommodation = 0,
                hasFood = 1,
                distanceMeters = 300,
                averageRating = 4.7,
                ratingsCount = 12
            ),
            Place(
                id = "2",
                name = "Hotel Pod Orłem",
                description = "Komfortowy hotel 4-gwiazdkowy ze strefą SPA i widokiem na rzekę.",
                address = "ul. Długa 45, Warszawa",
                category = "Nocleg",
                latitude = 52.2480,
                longitude = 21.0080,
                hasAccommodation = 1,
                hasFood = 0,
                distanceMeters = 800,
                averageRating = 4.4,
                ratingsCount = 8
            ),
            Place(
                id = "3",
                name = "Kawiarnia Centralna & Bistro",
                description = "Aromatyczna kawa specialty, świeże wypieki oraz zestawy śniadaniowe.",
                address = "ul. Marszałkowska 88, Warszawa",
                category = "Gastronomia",
                latitude = 52.2280,
                longitude = 21.0150,
                hasAccommodation = 0,
                hasFood = 1,
                distanceMeters = 1200,
                averageRating = 4.9,
                ratingsCount = 25
            ),
            Place(
                id = "4",
                name = "Pensjonat Słoneczny",
                description = "Przytulny pensjonat z domowym śniadaniem i darmowym parkingiem.",
                address = "ul. Zielona 5, Warszawa",
                category = "Nocleg",
                latitude = 52.2150,
                longitude = 21.0250,
                hasAccommodation = 1,
                hasFood = 1,
                distanceMeters = 2500,
                averageRating = 4.2,
                ratingsCount = 5
            ),
            Place(
                id = "5",
                name = "Pizzeria Bella Italia",
                description = "Prawdziwa włoska pizza z pieca opalanego drewnem oraz chrupiące makarony.",
                address = "ul. Floriańska 18, Warszawa",
                category = "Gastronomia",
                latitude = 52.2510,
                longitude = 21.0320,
                hasAccommodation = 0,
                hasFood = 1,
                distanceMeters = 3100,
                averageRating = 4.6,
                ratingsCount = 19
            )
        )
    }
}

private class SearchLocationPermissionRequired : Exception()

internal fun nearbyErrorMessage(error: HttpException, radiusMeters: Int): String =
    if (error.code() == 400 && radiusMeters > 50_000) {
        "Serwer odrzucił zapytanie (HTTP 400). Sprawdź, czy obsługuje promień ${radiusMeters / 1000} km."
    } else {
        error.localizedMessage ?: "Nie udało się pobrać miejsc."
    }

internal suspend fun searchPlacesFromApi(
    api: ApiService,
    name: String,
    address: String,
    visited: Boolean?,
    radiusMeters: Int,
    fallbackLocation: Pair<Double, Double>?,
    mine: Boolean = false,
    currentLocation: suspend () -> Pair<Double, Double>
): NearbyUiState.Success {
    val nameQuery = name.trim().takeIf { it.isNotEmpty() }
    val addressQuery = address.trim().takeIf { it.isNotEmpty() }
    val nearbySearch = nameQuery == null && addressQuery == null && visited == null
    val location = if (nearbySearch) currentLocation() else fallbackLocation ?: (52.23 to 21.01)
    val response = if (nearbySearch) {
        api.getNearbyPlaces(location.first, location.second, radiusMeters, limit = 100, mine = true.takeIf { mine })
    } else {
        api.searchPlaces(nameQuery, addressQuery, visited, limit = 100, mine = true.takeIf { mine })
    }
    return NearbyUiState.Success(response.items, location.first, location.second)
}
