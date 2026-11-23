package com.zwiedzak.data.network

import com.zwiedzak.data.auth.SessionAccess
import com.zwiedzak.data.auth.TokenSnapshot
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MineAuthenticationTest {
    @Test
    fun mineRequiresAuthenticationWithoutAnonymousFallback() {
        val session = object : SessionAccess {
            override fun tokenSnapshot() = TokenSnapshot("token", 1)
            override fun expireIfCurrent(snapshot: TokenSnapshot) = Unit
        }
        for (path in listOf("nearby", "search")) {
            val requests = mutableListOf<Request>()
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor({ session }, RetrofitInstance::isPublicRequest, RetrofitInstance::supportsOptionalAuthentication))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests.add(request)
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(401).message("Unauthorized").body("".toResponseBody()).build()
                }.build()
            val request = Request.Builder().url("http://localhost/api/v1/places/$path?mine=true").build()
            assertFalse(RetrofitInstance.isPublicRequest(request))
            assertFalse(RetrofitInstance.supportsOptionalAuthentication(request))
            client.newCall(request).execute().use { assertEquals(401, it.code) }
            assertEquals(1, requests.size)
            assertEquals("Bearer token", requests.single().header("Authorization"))

            val publicRequest = Request.Builder().url("http://localhost/api/v1/places/$path").build()
            assertTrue(RetrofitInstance.isPublicRequest(publicRequest))
            assertTrue(RetrofitInstance.supportsOptionalAuthentication(publicRequest))
        }
    }
}
