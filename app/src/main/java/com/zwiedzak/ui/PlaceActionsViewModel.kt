package com.zwiedzak.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zwiedzak.data.model.ApiErrorResponse
import com.zwiedzak.data.model.CreatePlaceRequest
import com.zwiedzak.data.model.DuplicatePlace
import com.zwiedzak.data.model.PlaceCategory
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.PlaceVisit
import com.zwiedzak.data.model.VisitStatus
import com.zwiedzak.data.network.loadVisitPages
import com.zwiedzak.data.model.editPlacePayload
import com.zwiedzak.data.model.parseEditPlaceResult
import com.zwiedzak.data.model.parseCreatePlaceConflict
import com.zwiedzak.data.model.isCreatedAvoidVisitConfirmed
import com.zwiedzak.data.model.SubmitRatingRequest
import com.zwiedzak.data.model.UpdateVisitStatusRequest
import com.zwiedzak.data.network.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

sealed interface PlaceActionState {
    data object Idle : PlaceActionState
    data object Loading : PlaceActionState
    data class Success(val message: String, val placeId: String? = null) : PlaceActionState
    data class Error(val message: String) : PlaceActionState
    data class VisitUncertain(
        val placeId: String,
        val status: String,
        val historyMessage: String
    ) : PlaceActionState
    data class EditConflict(val details: String, val payload: String) : PlaceActionState
    data class EditUncertain(val details: String) : PlaceActionState
    data class CreateConflict(val request: CreatePlaceRequest, val candidates: List<DuplicatePlace>) : PlaceActionState
    data class CreatedWithWarning(val message: String) : PlaceActionState
}

sealed interface VisitHistoryState {
    data object Loading : VisitHistoryState
    data class Success(val visits: List<PlaceVisit>) : VisitHistoryState
    data class Error(val message: String) : VisitHistoryState
}

sealed interface PlaceDetailsState {
    data object Idle : PlaceDetailsState
    data object Loading : PlaceDetailsState
    data class Success(val place: Place) : PlaceDetailsState
    data class Error(val message: String) : PlaceDetailsState
}

sealed interface CategoriesState {
    data object Idle : CategoriesState
    data object Loading : CategoriesState
    data class Success(val items: List<PlaceCategory>) : CategoriesState
    data class Error(val message: String) : CategoriesState
}

class PlaceActionsViewModel : ViewModel() {
    private var detailsJob: Job? = null
    private var categoriesJob: Job? = null
    private var historyJob: Job? = null
    private val _historyState = MutableStateFlow<VisitHistoryState>(VisitHistoryState.Loading)
    val historyState: StateFlow<VisitHistoryState> = _historyState.asStateFlow()

    private val _state = MutableStateFlow<PlaceActionState>(PlaceActionState.Idle)
    val state: StateFlow<PlaceActionState> = _state.asStateFlow()

    private val _detailsState = MutableStateFlow<PlaceDetailsState>(PlaceDetailsState.Idle)
    val detailsState: StateFlow<PlaceDetailsState> = _detailsState.asStateFlow()

    private val _categoriesState = MutableStateFlow<CategoriesState>(CategoriesState.Idle)
    val categoriesState: StateFlow<CategoriesState> = _categoriesState.asStateFlow()

    fun loadCategories() {
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch {
            _categoriesState.value = CategoriesState.Loading
            try {
                val categories = RetrofitInstance.api.getCategories().items
                _categoriesState.value = if (categories.isEmpty()) {
                    CategoriesState.Error("API nie zwróciło żadnych kategorii.")
                } else {
                    CategoriesState.Success(categories)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                _categoriesState.value = CategoriesState.Error(
                    "Nie udało się pobrać kategorii (${e.code()})."
                )
            } catch (e: Exception) {
                _categoriesState.value = CategoriesState.Error(
                    e.localizedMessage ?: "Nie udało się pobrać kategorii."
                )
            }
        }
    }

    fun loadVisitHistory(placeId: String) {
                    historyJob?.cancel()
                    historyJob = viewModelScope.launch {
                        _historyState.value = VisitHistoryState.Loading
                        try {
                            _historyState.value = VisitHistoryState.Success(
                                loadVisitPages(RetrofitInstance.api, placeId)
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _historyState.value = VisitHistoryState.Error(e.localizedMessage ?: "Nie udało się pobrać historii.")
                        }
                    }
                }

                fun editPlace(context: Context, original: Place, request: CreatePlaceRequest, imageUri: Uri?, caption: String) {
                    viewModelScope.launch {
                        val placeId = original.id
                        val json = editPlacePayload(original, request)
                        val unconfirmed = json.deepCopy().apply { remove("confirmDistinct") }.toString()
                        val conflict = _state.value as? PlaceActionState.EditConflict
                        if (request.confirmDistinct && conflict != null && conflict.payload != unconfirmed) {
                            json.remove("confirmDistinct")
                        }
                        _state.value = PlaceActionState.Loading
                        var savedPlaceId: String? = null
                        try {
                            val response = RetrofitInstance.nonRetryingApi.editPlace(
                                placeId, json.toString().toRequestBody("application/json".toMediaType())
                            )
                            if (!response.isSuccessful) {
                                if (response.code() == 409) {
                                    val details = response.errorBody()?.string().orEmpty()
                                    _state.value = if (details.contains("POSSIBLE_DUPLICATES") ||
                                        details.contains("NEARBY_PLACES_REQUIRE_CONFIRMATION")
                                    ) {
                                        PlaceActionState.EditConflict(details, unconfirmed)
                                    } else {
                                        PlaceActionState.Error("Konflikt zapisu (HTTP 409): $details")
                                    }
                                } else if (response.code() >= 500 || response.code() == 408) {
                                    showUncertainEdit(placeId, "HTTP ${response.code()}")
                                } else {
                                    _state.value = PlaceActionState.Error(httpErrorMessage(HttpException(response)))
                                }
                                return@launch
                            }
                            val result = try {
                                parseEditPlaceResult(response.body()?.use { it.string() }, placeId)
                            } catch (e: IOException) {
                                _state.value = PlaceActionState.EditUncertain(
                                    "Serwer potwierdził zapis, ale nie można ustalić miejsca: ${e.message} Sprawdź listę „Tylko moje” przed ponowieniem."
                                )
                                return@launch
                            }
                            val resultId = result.id
                            savedPlaceId = resultId
                            val imageResponse = imageUri?.let { uploadImageRequest(context.applicationContext, resultId, it, caption) }
                            _state.value = PlaceActionState.Success(
                                if (imageResponse != null && !imageResponse.isSuccessful) {
                                    "Zmiany zapisano, ale nie udało się dodać zdjęcia (HTTP ${imageResponse.code()})."
                                } else if (result.copied) "Utworzono prywatną kopię miejsca." else "Zmiany zapisano.",
                                placeId = resultId
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (savedPlaceId != null) {
                                _state.value = PlaceActionState.Success(
                                    "Zmiany zapisano, ale zdjęcia nie przesłano: ${e.localizedMessage}", savedPlaceId
                                )
                            } else {
                                showUncertainEdit(placeId, e.localizedMessage ?: "nieznany błąd")
                            }
                        }
                    }
                }

                private suspend fun showUncertainEdit(placeId: String, reason: String) {
                    val history = try {
                        val items = RetrofitInstance.api.getPlaceVisits(placeId, 50, 0).items
                        if (items.isEmpty()) "Brak wizyt w historii." else items.joinToString("\n") { it.visitedAt }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "Nie udało się pobrać historii: ${e.localizedMessage ?: "nieznany błąd"}"
                    }
                    loadVisitHistory(placeId)
                    _state.value = PlaceActionState.EditUncertain("Powód: $reason\n\nOstatnie wizyty (UTC):\n$history")
                }

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

    fun createPlace(
        context: Context,
        request: CreatePlaceRequest,
        imageUri: Uri?,
        caption: String
    ) {
        viewModelScope.launch {
            _state.value = PlaceActionState.Loading
            var placeCreated = false
            var statusWarning: String? = null
            try {
                val response = RetrofitInstance.nonRetryingApi.createPlace(request)
                if (response.code() == 409) {
                    val candidates = parseCreatePlaceConflict(response.errorBody()?.use { it.string() }.orEmpty())
                    _state.value = if (candidates != null) {
                        PlaceActionState.CreateConflict(request.copy(confirmDistinct = false), candidates)
                    } else {
                        PlaceActionState.Error("Serwer odrzucił dodanie miejsca (HTTP 409).")
                    }
                    return@launch
                }
                if (!response.isSuccessful) throw HttpException(response)
                placeCreated = true

                val locationPlaceId = response.headers()["Location"]
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
                val responseBody = if (request.createVisitStatus != null || (imageUri != null && locationPlaceId == null)) {
                    response.body()?.use { it.string() }
                } else null
                if (request.createVisitStatus == VisitStatus.AVOID.apiValue &&
                    !isCreatedAvoidVisitConfirmed(responseBody)) {
                    statusWarning = "Nie potwierdzono statusu „Nie wracam” i daty wizyty. Sprawdź miejsce; może być potrzebna aktualizacja serwera."
                }
                if (imageUri == null) {
                    _state.value = if (statusWarning == null) {
                        PlaceActionState.Success("Miejsce zostało dodane.")
                    } else {
                        PlaceActionState.CreatedWithWarning("Miejsce zostało dodane. $statusWarning")
                    }
                    return@launch
                }

                val placeId = locationPlaceId ?: responseBody?.let(::extractCreatedPlaceId)

                if (placeId == null) {
                    _state.value = PlaceActionState.CreatedWithWarning(
                        listOfNotNull("Miejsce dodano, ale API nie zwróciło jego ID, więc zdjęcia nie można było dodać.", statusWarning)
                            .joinToString(" ")
                    )
                    return@launch
                }

                val imageResponse = uploadImageRequest(
                    context.applicationContext,
                    placeId,
                    imageUri,
                    caption
                )
                _state.value = if (imageResponse.isSuccessful) {
                    if (statusWarning == null) PlaceActionState.Success("Miejsce i zdjęcie zostały dodane.")
                    else PlaceActionState.CreatedWithWarning("Miejsce i zdjęcie zostały dodane. $statusWarning")
                } else {
                    PlaceActionState.CreatedWithWarning(
                        listOfNotNull("Miejsce dodano, ale zdjęcie nie zostało przesłane (HTTP ${imageResponse.code()}).", statusWarning)
                            .joinToString(" ")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (placeCreated) {
                    _state.value = PlaceActionState.CreatedWithWarning(
                        listOfNotNull("Miejsce dodano, ale nie udało się przesłać zdjęcia (HTTP ${e.code()}).", statusWarning)
                            .joinToString(" ")
                    )
                } else {
                    _state.value = PlaceActionState.Error(
                        if (e.code() >= 500 || e.code() == 408) {
                            "Nie wiadomo, czy miejsce zostało dodane (HTTP ${e.code()}). " +
                                "Sprawdź swoją listę miejsc przed ponowieniem."
                        } else {
                            httpErrorMessage(e)
                        }
                    )
                }
            } catch (e: Exception) {
                val message = e.localizedMessage ?: "Wystąpił nieznany błąd."
                _state.value = if (placeCreated) {
                    PlaceActionState.CreatedWithWarning(
                        listOfNotNull(
                            if (imageUri == null) {
                                "Miejsce dodano, ale nie udało się potwierdzić statusu: $message"
                            } else {
                                "Miejsce dodano, ale nie udało się potwierdzić statusu lub przesłać zdjęcia: $message"
                            },
                            statusWarning
                        ).joinToString(" ")
                    )
                } else if (e is IOException) {
                    PlaceActionState.Error(
                        "Nie wiadomo, czy miejsce zostało dodane. " +
                            "Sprawdź swoją listę miejsc przed ponowieniem: $message"
                    )
                } else {
                    PlaceActionState.Error(message)
                }
            }
        }
    }

    fun updateVisitSelection(placeId: String, status: VisitStatus) {
        viewModelScope.launch {
            _state.value = PlaceActionState.Loading
            try {
                val body = JsonObject().apply { addProperty("visitStatus", status.apiValue) }
                    .toString().toRequestBody("application/json".toMediaType())
                val response = RetrofitInstance.nonRetryingApi.editPlace(placeId, body)
                response.body()?.close()
                if (!response.isSuccessful) {
                    if (response.code() >= 500 || response.code() == 408) {
                        _state.value = PlaceActionState.Error(
                            "Nie wiadomo, czy status został zapisany (HTTP ${response.code()}). Sprawdź odświeżony status."
                        )
                        loadPlaceDetails(placeId)
                    } else {
                        _state.value = PlaceActionState.Error(httpErrorMessage(HttpException(response)))
                    }
                    return@launch
                }
                _state.value = PlaceActionState.Success("Zapisano status wizyty.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _state.value = PlaceActionState.Error(
                    "Nie wiadomo, czy status został zapisany. Sprawdź odświeżony status: ${e.localizedMessage}"
                )
                loadPlaceDetails(placeId)
            } catch (e: Exception) {
                _state.value = PlaceActionState.Error(
                    "Nie udało się zapisać statusu: ${e.localizedMessage ?: "nieznany błąd"}."
                )
            }
        }
    }

    fun updateVisitStatus(
        placeId: String,
        status: String,
        context: Context? = null,
        imageUri: Uri? = null,
        caption: String = ""
    ) {
        viewModelScope.launch {
            _state.value = PlaceActionState.Loading
            try {
                val response = RetrofitInstance.nonRetryingApi.updateVisitStatus(
                    placeId,
                    UpdateVisitStatusRequest(status)
                )
                if (!response.isSuccessful) {
                    if (response.code() >= 500 || response.code() == 408) {
                        showUncertainVisit(placeId, status, "HTTP ${response.code()}")
                    } else {
                        _state.value = PlaceActionState.Error(httpErrorMessage(HttpException(response)))
                    }
                    return@launch
                }
                var message = "Zapisano status wizyty."
                if (context != null && imageUri != null) {
                    try {
                        val imageResponse = uploadImageRequest(context.applicationContext, placeId, imageUri, caption)
                        message = if (imageResponse.isSuccessful) {
                            "Zapisano status wizyty i dodano zdjęcie."
                        } else {
                            "Zapisano status wizyty, ale zdjęcia nie przesłano (HTTP ${imageResponse.code()})."
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        message = "Zapisano status wizyty, ale zdjęcia nie przesłano: ${e.localizedMessage}"
                    }
                }
                _state.value = PlaceActionState.Success(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showUncertainVisit(placeId, status, e.localizedMessage ?: "nieznany błąd")
            }
        }
    }

    private suspend fun showUncertainVisit(placeId: String, status: String, reason: String) {
        val historyMessage = try {
            val visits = RetrofitInstance.api.getPlaceVisits(placeId).items
            if (visits.isEmpty()) {
                "W historii nie ma zapisanych wizyt."
            } else {
                "Ostatnie wizyty (UTC):\n" + visits.joinToString("\n") { it.visitedAt }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Nie udało się sprawdzić historii: ${e.localizedMessage ?: "nieznany błąd"}."
        }
        _state.value = PlaceActionState.VisitUncertain(
            placeId,
            status,
            "Przyczyna: $reason.\n\n$historyMessage"
        )
        loadPlaceDetails(placeId)
    }

    fun uploadImage(context: Context, placeId: String, uri: Uri, caption: String) =
        perform("Dodano zdjęcie.") {
            uploadImageRequest(context.applicationContext, placeId, uri, caption)
        }

    fun ratePlace(placeId: String, score: Int) =
        perform("Zapisano ocenę.") {
            RetrofitInstance.api.submitRating(placeId, SubmitRatingRequest(score))
        }

    fun clearState() {
        _state.value = PlaceActionState.Idle
    }

    private suspend fun uploadImageRequest(
        context: Context,
        placeId: String,
        uri: Uri,
        caption: String
    ): Response<Unit> {
        val contentType = context.contentResolver.getType(uri)
            ?.takeIf { it.startsWith("image/", ignoreCase = true) }
            ?: throw IOException("Nie można odczytać wybranego pliku jako zdjęcia.")
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(contentType)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) }
            ?: "img"
        val imageFile = withContext(Dispatchers.IO) {
            val file = File.createTempFile("zwiedzak-image-", ".$extension", context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Nie można otworzyć wybranego zdjęcia.")
                input.use { source ->
                    file.outputStream().use { destination -> source.copyTo(destination) }
                }
                if (file.length() == 0L) {
                    throw IOException("Wybrane zdjęcie jest puste.")
                }
                file
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }

        return try {
            val imagePart = MultipartBody.Part.createFormData(
                "image",
                "photo.$extension",
                imageFile.asRequestBody(contentType.toMediaType())
            )
            val captionPart = caption.trim().takeIf { it.isNotEmpty() }
                ?.toRequestBody("text/plain".toMediaType())
            RetrofitInstance.nonRetryingApi.uploadPlaceImage(placeId, imagePart, captionPart)
        } finally {
            imageFile.delete()
        }
    }

    private fun extractCreatedPlaceId(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        val root = try {
            JsonParser.parseString(responseBody)
        } catch (_: Exception) {
            return null
        }
        return findPlaceId(root)
    }

    private fun findPlaceId(element: JsonElement): String? {
        if (!element.isJsonObject) return null
        val objectValue = element.asJsonObject
        for (key in listOf("id", "placeId", "place_id")) {
            objectValue.get(key)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        for (key in listOf("place", "item", "data")) {
            objectValue.get(key)?.let(::findPlaceId)?.let { return it }
        }
        return null
    }

    private fun httpErrorMessage(e: HttpException): String {
        val serverMessage = try {
            e.response()?.errorBody()?.string()
                ?.takeIf { it.isNotBlank() }
                ?.let { Gson().fromJson(it, ApiErrorResponse::class.java) }
                ?.let { it.message ?: it.error }
        } catch (_: Exception) {
            null
        }
        return serverMessage ?: when (e.code()) {
            401 -> "Sesja wygasła. Zaloguj się ponownie."
            403 -> "Brak uprawnień do tej operacji."
            else -> "Serwer odrzucił żądanie (${e.code()})."
        }
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
                _state.value = PlaceActionState.Error(httpErrorMessage(e))
            } catch (e: Exception) {
                _state.value = PlaceActionState.Error(
                    e.localizedMessage ?: "Nie udało się wykonać operacji."
                )
            }
        }
    }
}
