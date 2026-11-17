package com.zwiedzak.data.network

import android.content.Context
import com.zwiedzak.data.auth.AuthTokenManager
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {
    private var baseUrl = "http://miejsca.webd.pro/zwiedzak/"
    private var apiServiceInstance: ApiService? = null
    private var nonRetryingApiServiceInstance: ApiService? = null
    private var tokenManager: AuthTokenManager? = null

    fun init(context: Context) {
        tokenManager = AuthTokenManager.getInstance(context)
    }

    fun setBaseUrl(url: String) {
        val formattedUrl = if (url.endsWith("/")) url else "$url/"
        if (baseUrl != formattedUrl) {
            baseUrl = formattedUrl
            apiServiceInstance = null
            nonRetryingApiServiceInstance = null
        }
    }

    fun getBaseUrl(): String = baseUrl

    val api: ApiService
        get() {
            if (apiServiceInstance == null) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.NONE
                }

                val authInterceptor = AuthInterceptor(
                    session = { tokenManager },
                    isPublicRequest = ::isPublicRequest,
                    supportsOptionalAuthentication = ::supportsOptionalAuthentication
                )

                val client = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                apiServiceInstance = retrofit.create(ApiService::class.java)
                nonRetryingApiServiceInstance = retrofit.newBuilder()
                    .client(
                        client.newBuilder()
                            .retryOnConnectionFailure(false)
                            .followRedirects(false)
                            .followSslRedirects(false)
                            .addInterceptor { chain ->
                                val request = chain.request()
                                val body = request.body
                                if (body == null) {
                                    chain.proceed(request)
                                } else {
                                    val oneShotBody = object : RequestBody() {
                                        override fun contentType(): MediaType? = body.contentType()
                                        override fun contentLength(): Long = body.contentLength()
                                        override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                                        override fun isOneShot(): Boolean = true
                                    }
                                    chain.proceed(request.newBuilder().method(request.method, oneShotBody).build())
                                }
                            }
                            .build()
                    )
                    .build()
                    .create(ApiService::class.java)
            }
            return apiServiceInstance!!
        }

    val nonRetryingApi: ApiService
        get() {
            if (nonRetryingApiServiceInstance == null) {
                api
            }
            return nonRetryingApiServiceInstance!!
        }

    internal fun supportsOptionalAuthentication(request: okhttp3.Request): Boolean {
        val path = request.url.encodedPath
        return request.method == "GET" && request.url.queryParameter("mine") != "true" && (
            path.endsWith("/api/v1/places/nearby") ||
                (path.endsWith("/api/v1/places/search") &&
                    request.url.queryParameter("visited") == null)
            )
    }

    internal fun isPublicRequest(request: okhttp3.Request): Boolean {
        val path = request.url.encodedPath
        if (request.url.queryParameter("mine") == "true" &&
            (path.endsWith("/api/v1/places/nearby") || path.endsWith("/api/v1/places/search"))
        ) return false
        return path.endsWith("/health") ||
            path.endsWith("/api/v1/categories") ||
            path.endsWith("/api/v1/places/nearby") ||
            path.endsWith("/api/v1/auth/register") ||
            path.endsWith("/api/v1/auth/register/verify") ||
            path.endsWith("/api/v1/auth/register/resend") ||
            path.endsWith("/api/v1/auth/login") ||
            (
                path.endsWith("/api/v1/places/search") &&
                    request.url.queryParameter("visited") == null
                )
    }
}
