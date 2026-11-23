package com.zwiedzak.data.network

import com.zwiedzak.data.auth.SessionAccess
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal class AuthInterceptor(
    private val session: () -> SessionAccess?,
    private val isPublicRequest: (Request) -> Boolean,
    private val supportsOptionalAuthentication: (Request) -> Boolean
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val sessionAccess = session()
        val snapshot = sessionAccess?.tokenSnapshot()
        val useToken = snapshot != null &&
            (!isPublicRequest(original) || supportsOptionalAuthentication(original))
        val request = if (useToken) {
            original.newBuilder().header("Authorization", "Bearer ${snapshot!!.token}").build()
        } else {
            original
        }
        val response = chain.proceed(request)
        if (response.code != 401 || !useToken) return response

        sessionAccess?.expireIfCurrent(snapshot!!)
        if (!supportsOptionalAuthentication(original)) return response

        response.close()
        return chain.proceed(original.newBuilder().removeHeader("Authorization").build())
    }
}
