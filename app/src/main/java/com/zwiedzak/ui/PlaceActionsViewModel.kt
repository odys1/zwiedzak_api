package com.zwiedzak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zwiedzak.data.model.ApiErrorResponse
import com.zwiedzak.data.model.AddPlaceImageRequest
import com.zwiedzak.data.model.CreatePlaceRequest
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.SubmitRatingRequest
import com.zwiedzak.data.model.UpdateVisitStatusRequest
import com.zwiedzak.data.network.RetrofitInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Response

sealed interface PlaceActionState {
    data object Idle : PlaceActionState
    data object Loading : PlaceActionState
    data class Success(val message: String) : PlaceActionState
    data class Error(val message: String) : PlaceActionState
}

sealed interface PlaceDetailsState {
    data object Idle : PlaceDetailsState
    data object Loading : PlaceDetailsState
    data class Success(val place: Place) : PlaceDetailsState
    data class Error(val message: String) : PlaceDetailsState
}

class PlaceActionsViewModel : ViewModel() {
    private var detailsJob: Job? = null

    private val _state = MutableStateFlow<PlaceActionState>(PlaceActionState.Idle)
    val state: StateFlow<PlaceActionState> = _state.asStateFlow()

    private val _detailsState = MutableStateFlow<PlaceDetailsState>(PlaceDetailsState.Idle)
    val detailsState: StateFlow<PlaceDetailsState> = _detailsState.asStateFlow()

    fun loadPlaceDetails(placeId: String) {
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            _detailsState.value = PlaceDetailsState.Loading
            try {
                _detailsState.value = PlaceDetailsState.Success(
                    RetrofitInstance.api.getPlaceDetails(placeId)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                _detailsState.value = PlaceDetailsState.Error(
                    "Nie udało się pobrać szczegółów miejsca (${e.code()})."
                )
            } catch (e: Exception) {
                _detailsState.value = PlaceDetailsState.Error(
                    e.localizedMessage ?: "Nie udało się pobrać szczegółów miejsca."
                )
            }
        }
    }

    fun createPlace(request: CreatePlaceRequest) =
        perform("Miejsce zostało dodane.") {
            RetrofitInstance.api.createPlace(request)
        }

    fun updateVisitStatus(placeId: String, status: String) =
        perform("Zapisano status wizyty.") {
            RetrofitInstance.api.updateVisitStatus(
                placeId,
                UpdateVisitStatusRequest(status)
            )
        }

    fun addImage(placeId: String, imageUrl: String, caption: String) =
        perform("Dodano zdjęcie.") {
            RetrofitInstance.api.addPlaceImage(
                placeId,
                AddPlaceImageRequest(imageUrl, caption.trim().takeIf { it.isNotEmpty() })
            )
        }

    fun ratePlace(placeId: String, score: Int) =
        perform("Zapisano ocenę.") {
            RetrofitInstance.api.submitRating(placeId, SubmitRatingRequest(score))
        }

    fun clearState() {
        _state.value = PlaceActionState.Idle
    }

    private fun perform(
        successMessage: String,
        request: suspend () -> Response<Unit>
    ) {
        viewModelScope.launch {
            _state.value = PlaceActionState.Loading
            try {
                val response = request()
                if (!response.isSuccessful) {
                    throw HttpException(response)
                }
                _state.value = PlaceActionState.Success(successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val serverMessage = try {
                    e.response()?.errorBody()?.string()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { Gson().fromJson(it, ApiErrorResponse::class.java) }
                        ?.let { it.message ?: it.error }
                } catch (_: Exception) {
                    null
                }
                val message = serverMessage ?: when (e.code()) {
                    401 -> "Sesja wygasła. Zaloguj się ponownie."
                    403 -> "Brak uprawnień do tej operacji."
                    else -> "Serwer odrzucił żądanie (${e.code()})."
                }
                _state.value = PlaceActionState.Error(message)
            } catch (e: Exception) {
                _state.value = PlaceActionState.Error(
                    e.localizedMessage ?: "Nie udało się wykonać operacji."
                )
            }
        }
    }
}
