package com.zwiedzak.data.network

import android.content.Context
import com.zwiedzak.data.auth.AuthTokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    private var baseUrl = "http://miejsca.webd.pro/zwiedzak/"
    private var apiServiceInstance: ApiService? = null
    private var tokenManager: AuthTokenManager? = null

    fun init(context: Context) {
        tokenManager = AuthTokenManager.getInstance(context)
    }

    fun setBaseUrl(url: String) {
        val formattedUrl = if (url.endsWith("/")) url else "$url/"
        if (baseUrl != formattedUrl) {
            baseUrl = formattedUrl
            apiServiceInstance = null
        }
    }

    fun getBaseUrl(): String = baseUrl

    val api: ApiService
        get() {
            if (apiServiceInstance == null) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE
                }

                val authInterceptor = Interceptor { chain ->
                    val original = chain.request()
                    val token = tokenManager?.getToken()
                    val requestBuilder = original.newBuilder()
                    if (!token.isNullOrBlank()) {
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                    chain.proceed(requestBuilder.build())
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                apiServiceInstance = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService::class.java)
            }
            return apiServiceInstance!!
        }
}
