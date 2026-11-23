package com.zwiedzak.data.network

import com.zwiedzak.data.auth.SessionAccess
import com.zwiedzak.data.auth.TokenSnapshot
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInterceptorTest {
    private class FakeSession : SessionAccess {
        var current: TokenSnapshot? = TokenSnapshot("old-token", 1)
        var expirations = 0

        override fun tokenSnapshot(): TokenSnapshot? = current
        override fun expireIfCurrent(snapshot: TokenSnapshot) {
            if (current == snapshot) {
                current = null
                expirations++
            }
        }
    }

    @Test
    fun protectedUnauthorizedExpiresSessionOnlyOnce() {
        val session = FakeSession()
        val client = client(session) { request ->
            assertNotNull(request.header("Authorization"))
            401
        }
        client.newCall(Request.Builder().url("http://localhost/protected").build()).execute().close()
        assertNull(session.current)
        assertEquals(1, session.expirations)
    }

    @Test
    fun optionalUnauthorizedExpiresSessionAndRetriesAnonymously() {
        val session = FakeSession()
        val headers = mutableListOf<String?>()
        val client = client(session) { request ->
            headers.add(request.header("Authorization"))
            if (headers.size == 1) 401 else 200
        }
        val response = client.newCall(Request.Builder().url("http://localhost/optional").build()).execute()
        response.use { assertEquals(200, it.code) }
        assertEquals(listOf("Bearer old-token", null), headers)
        assertEquals(1, session.expirations)
    }

    @Test
    fun loginUnauthorizedAndServerErrorsDoNotExpireSession() {
        val session = FakeSession()
        val client = client(session) { request ->
            if (request.url.encodedPath == "/login") {
                assertNull(request.header("Authorization"))
                401
            } else {
                500
            }
        }
        client.newCall(Request.Builder().url("http://localhost/login").build()).execute().close()
        client.newCall(Request.Builder().url("http://localhost/protected").build()).execute().close()
        assertNotNull(session.current)
        assertEquals(0, session.expirations)
    }

    @Test
    fun lateUnauthorizedDoesNotExpireNewSession() {
        val session = FakeSession()
        val client = client(session) {
            session.current = TokenSnapshot("new-token", 2)
            401
        }
        client.newCall(Request.Builder().url("http://localhost/protected").build()).execute().close()
        assertEquals(TokenSnapshot("new-token", 2), session.current)
        assertEquals(0, session.expirations)
    }

    private fun client(session: FakeSession, reply: (Request) -> Int): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                AuthInterceptor(
                    session = { session },
                    isPublicRequest = { it.url.encodedPath == "/login" || it.url.encodedPath == "/optional" },
                    supportsOptionalAuthentication = { it.url.encodedPath == "/optional" }
                )
            )
            .addInterceptor { chain ->
                val request = chain.request()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(reply(request))
                    .message("test")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
}
