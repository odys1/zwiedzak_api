package com.zwiedzak.ui.auth

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Authenticated(val email: String) : AuthState
    data class Unauthenticated(val errorMessage: String? = null) : AuthState
    data class VerificationRequired(
        val email: String,
        val expiresIn: Int,
        val resendAfter: Int,
        val resendAvailableAt: Long,
        val loading: Boolean = false,
        val errorMessage: String? = null,
        val message: String? = null
    ) : AuthState
}
