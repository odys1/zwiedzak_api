package com.zwiedzak.ui.auth

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zwiedzak.data.auth.AuthTokenManager
import com.zwiedzak.data.model.ApiErrorResponse
import com.zwiedzak.data.model.LoginRequest
import com.zwiedzak.data.model.RegisterRequest
import com.zwiedzak.data.model.ResendRegistrationRequest
import com.zwiedzak.data.model.VerifyRegistrationRequest
import com.zwiedzak.data.network.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun checkAuthStatus(context: Context) {
        if (_authState.value !is AuthState.Idle) return
        val tokenManager = AuthTokenManager.getInstance(context)
        if (tokenManager.isLoggedIn()) {
            val email = tokenManager.getEmail() ?: "Zalogowany użytkownik"
            _authState.value = AuthState.Authenticated(email)
        } else {
            _authState.value = AuthState.Unauthenticated()
        }
    }

    fun login(context: Context, email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Unauthenticated("Uzupełnij e-mail i hasło.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = RetrofitInstance.api.login(LoginRequest(email.trim(), password))
                val tokenManager = AuthTokenManager.getInstance(context)
                tokenManager.saveToken(response.accessToken, email.trim())

                _authState.value = AuthState.Authenticated(email.trim())
            } catch (e: HttpException) {
                val errorMessage = parseHttpError(e) ?: when (e.code()) {
                    401 -> "Nieprawidłowy e-mail lub hasło."
                    400 -> "Niepoprawne dane logowania (hasło min. 10 znaków)."
                    else -> "Błąd logowania (${e.code()})."
                }
                _authState.value = AuthState.Unauthenticated(errorMessage)
            } catch (e: Exception) {
                _authState.value = AuthState.Unauthenticated("Brak połączenia z serwerem API. Upewnij się, że serwer działa.")
            }
        }
    }

    fun register(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Unauthenticated("Uzupełnij e-mail i hasło.")
            return
        }

        if (password.length < 10) {
            _authState.value = AuthState.Unauthenticated("Hasło musi mieć co najmniej 10 znaków.")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = RetrofitInstance.api.register(RegisterRequest(email.trim(), password))
                if (response.status != "verification_required" || response.expiresIn <= 0 || response.resendAfter < 0) {
                    _authState.value = AuthState.Unauthenticated("Nieprawidłowa odpowiedź serwera rejestracji.")
                    return@launch
                }
                _authState.value = AuthState.VerificationRequired(
                    email = email.trim(),
                    expiresIn = response.expiresIn,
                    resendAfter = response.resendAfter,
                    resendAvailableAt = SystemClock.elapsedRealtime() + response.resendAfter * 1000L
                )
            } catch (e: HttpException) {
                val errorMessage = parseHttpError(e) ?: when (e.code()) {
                    409 -> "Ten adres e-mail jest już zarejestrowany."
                    400 -> "Niepoprawny format e-maila lub zbyt krótkie hasło (min. 10 znaków)."
                    else -> "Błąd rejestracji (${e.code()})."
                }
                _authState.value = AuthState.Unauthenticated(errorMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _authState.value = AuthState.Unauthenticated("Brak połączenia z serwerem API. Upewnij się, że serwer działa.")
            } catch (e: Exception) {
                _authState.value = AuthState.Unauthenticated("Nie udało się przetworzyć odpowiedzi serwera rejestracji.")
            }
        }
    }

    fun verifyRegistration(context: Context, code: String) {
        val state = _authState.value as? AuthState.VerificationRequired ?: return
        if (state.loading) return
        if (code.isBlank()) {
            _authState.value = state.copy(errorMessage = "Wprowadź kod weryfikacyjny.", message = null)
            return
        }

        _authState.value = state.copy(loading = true, errorMessage = null, message = null)
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.verifyRegistration(
                    VerifyRegistrationRequest(state.email, code)
                )
                if (response.accessToken.isNullOrBlank()) {
                    _authState.value = state.copy(errorMessage = "Serwer nie zwrócił tokenu dostępu.")
                    return@launch
                }
                AuthTokenManager.getInstance(context).saveToken(response.accessToken, state.email)
                _authState.value = AuthState.Authenticated(state.email)
            } catch (e: HttpException) {
                _authState.value = state.copy(
                    errorMessage = parseHttpError(e) ?: "Nie udało się potwierdzić kodu (${e.code()})."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _authState.value = state.copy(errorMessage = "Brak połączenia z serwerem API.")
            } catch (e: Exception) {
                _authState.value = state.copy(errorMessage = "Nie udało się przetworzyć odpowiedzi serwera.")
            }
        }
    }

    fun resendRegistration() {
        val state = _authState.value as? AuthState.VerificationRequired ?: return
        if (state.loading || SystemClock.elapsedRealtime() < state.resendAvailableAt) return
        _authState.value = state.copy(loading = true, errorMessage = null, message = null)
        viewModelScope.launch {
            try {
                RetrofitInstance.api.resendRegistration(ResendRegistrationRequest(state.email))
                _authState.value = state.copy(
                    resendAvailableAt = SystemClock.elapsedRealtime() + state.resendAfter * 1000L,
                    message = "Wysłano ponownie kod weryfikacyjny."
                )
            } catch (e: HttpException) {
                _authState.value = state.copy(
                    errorMessage = parseHttpError(e) ?: "Nie udało się wysłać kodu (${e.code()})."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _authState.value = state.copy(errorMessage = "Brak połączenia z serwerem API.")
            } catch (e: Exception) {
                _authState.value = state.copy(errorMessage = "Nie udało się przetworzyć odpowiedzi serwera.")
            }
        }
    }

    fun cancelRegistration() {
        val state = _authState.value as? AuthState.VerificationRequired ?: return
        if (!state.loading) _authState.value = AuthState.Unauthenticated()
    }

    fun logout(context: Context) {
        val tokenManager = AuthTokenManager.getInstance(context)
        tokenManager.clear()
        _authState.value = AuthState.Unauthenticated()
    }

    fun clearError() {
        if (_authState.value is AuthState.Unauthenticated) {
            _authState.value = AuthState.Unauthenticated(null)
        }
    }

    private fun parseHttpError(e: HttpException): String? {
        return try {
            val errorJson = e.response()?.errorBody()?.string()
            if (!errorJson.isNullOrBlank()) {
                val apiError = Gson().fromJson(errorJson, ApiErrorResponse::class.java)
                when (apiError.error) {
                    "EMAIL_ALREADY_REGISTERED" -> "Ten e-mail jest już zarejestrowany."
                    "INVALID_CREDENTIALS" -> "Nieprawidłowy e-mail lub hasło."
                    "INVALID_REQUEST" -> "Nieprawidłowy format danych."
                    else -> apiError.message ?: apiError.error
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
