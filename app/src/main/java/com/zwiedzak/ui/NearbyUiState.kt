package com.zwiedzak.ui

import com.zwiedzak.data.model.Place

sealed interface NearbyUiState {
    object Idle : NearbyUiState
    object PermissionRequired : NearbyUiState
    object Loading : NearbyUiState
    data class Success(
        val places: List<Place>,
        val userLat: Double,
        val userLng: Double,
        val isMockData: Boolean = false,
        val apiErrorMessage: String? = null
    ) : NearbyUiState
    data class Error(val message: String) : NearbyUiState
}
