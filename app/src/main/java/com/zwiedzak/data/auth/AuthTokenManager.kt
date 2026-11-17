package com.zwiedzak.data.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TokenSnapshot(val token: String, val generation: Long)

interface SessionAccess {
    fun tokenSnapshot(): TokenSnapshot?
    fun expireIfCurrent(snapshot: TokenSnapshot)
}

class AuthTokenManager(context: Context) : SessionAccess {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("zwiedzak_auth_prefs", Context.MODE_PRIVATE)
    private var generation = 0L
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired = _sessionExpired.asStateFlow()

    @Synchronized
    fun saveToken(token: String, email: String? = null) {
        generation++
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            email?.let { putString(KEY_EMAIL, it) }
            apply()
        }
        _sessionExpired.value = false
    }

    @Synchronized
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    @Synchronized
    fun getEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    @Synchronized
    fun clear() {
        generation++
        prefs.edit().clear().apply()
        _sessionExpired.value = false
    }

    @Synchronized
    override fun tokenSnapshot(): TokenSnapshot? =
        getToken()?.takeIf { it.isNotBlank() }?.let { TokenSnapshot(it, generation) }

    @Synchronized
    override fun expireIfCurrent(snapshot: TokenSnapshot) {
        if (snapshot != tokenSnapshot()) return
        generation++
        prefs.edit().clear().apply()
        _sessionExpired.value = true
    }

    @Synchronized
    fun acknowledgeExpiration() {
        _sessionExpired.value = false
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
