package com.zwiedzak.ui.auth

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Authenticated(val email: String) : AuthState
    data class Unauthenticated(val errorMessage: String? = null) : AuthState
}
