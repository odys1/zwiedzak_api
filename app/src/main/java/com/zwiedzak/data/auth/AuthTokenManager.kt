package com.zwiedzak.data.auth

import android.content.Context
import android.content.SharedPreferences

class AuthTokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("zwiedzak_auth_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String, email: String? = null) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            email?.let { putString(KEY_EMAIL, it) }
            apply()
        }
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrBlank()
    }

    companion object {
        private const val KEY_TOKEN = "access_token"
        private const val KEY_EMAIL = "user_email"

        @Volatile
        private var INSTANCE: AuthTokenManager? = null

        fun getInstance(context: Context): AuthTokenManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthTokenManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
