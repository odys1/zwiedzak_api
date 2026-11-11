package com.zwiedzak.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zwiedzak.data.auth.AuthTokenManager
import com.zwiedzak.data.model.ApiErrorResponse
import com.zwiedzak.data.model.LoginRequest
import com.zwiedzak.data.model.RegisterRequest
import com.zwiedzak.data.network.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun checkAuthStatus(context: Context) {
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

    fun register(context: Context, email: String, password: String) {
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
                val tokenManager = AuthTokenManager.getInstance(context)
                tokenManager.saveToken(response.accessToken, email.trim())

                _authState.value = AuthState.Authenticated(email.trim())
            } catch (e: HttpException) {
                val errorMessage = parseHttpError(e) ?: when (e.code()) {
                    409 -> "Ten adres e-mail jest już zarejestrowany."
                    400 -> "Niepoprawny format e-maila lub zbyt krótkie hasło (min. 10 znaków)."
                    else -> "Błąd rejestracji (${e.code()})."
                }
                _authState.value = AuthState.Unauthenticated(errorMessage)
            } catch (e: Exception) {
                _authState.value = AuthState.Unauthenticated("Brak połączenia z serwerem API. Upewnij się, że serwer działa.")
            }
        }
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
