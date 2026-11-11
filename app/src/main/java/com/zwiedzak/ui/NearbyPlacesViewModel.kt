package com.zwiedzak.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.network.RetrofitInstance
import com.zwiedzak.location.LocationTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun searchPlaces(name: String, address: String, visited: Boolean?) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchUiState.value = NearbyUiState.Loading
            delay(350)
            try {
                val response = RetrofitInstance.api.searchPlaces(
                    name = name.trim().takeIf { it.isNotEmpty() },
                    address = address.trim().takeIf { it.isNotEmpty() },
                    visited = visited,
                    limit = 100
                )
                val currentLocation = _uiState.value as? NearbyUiState.Success
                _searchUiState.value = NearbyUiState.Success(
                    places = response.items,
                    userLat = currentLocation?.userLat ?: 52.23,
                    userLng = currentLocation?.userLng ?: 21.01
                )
            } catch (e: CancellationException) {
                throw e
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
    ): NearbyUiState.Success {
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
        } catch (_: Exception) {
            NearbyUiState.Success(
                places = getMockPlaces(),
                userLat = lat,
                userLng = lng,
                isMockData = true
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
