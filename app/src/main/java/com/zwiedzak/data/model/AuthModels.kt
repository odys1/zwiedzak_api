package com.zwiedzak.data.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)

data class LoginRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)

data class AuthResponse(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("tokenType")
    val tokenType: String? = "Bearer",
    @SerializedName("expiresIn")
    val expiresIn: Int? = 900
)

data class ApiErrorResponse(
    @SerializedName("error")
    val error: String?,
    @SerializedName("message")
    val message: String?
)
