package com.zwiedzak.data.network

import com.zwiedzak.data.model.RegisterRequest
import com.zwiedzak.data.model.ResendRegistrationRequest
import com.zwiedzak.data.model.VerifyRegistrationRequest
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RegistrationApiTest {
    @Test
    fun registrationRequiresVerificationAndPreservesLeadingZeroes() = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            requests.add(request.url.encodedPath to buffer.readUtf8())
            val (code, body) = when (request.url.encodedPath) {
                "/api/v1/auth/register" ->
                    202 to """{"status":"verification_required","expiresIn":600,"resendAfter":60}"""
                "/api/v1/auth/register/verify" ->
                    201 to """{"accessToken":"token","tokenType":"Bearer","expiresIn":900}"""
                "/api/v1/auth/register/resend" -> 204 to ""
                else -> error("Unexpected endpoint")
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)

        val registration = api.register(RegisterRequest("user@example.com", "long-password"))
        assertEquals("verification_required", registration.status)
        assertEquals(600, registration.expiresIn)
        assertEquals(60, registration.resendAfter)
        val auth = api.verifyRegistration(VerifyRegistrationRequest("user@example.com", "004217"))
        assertEquals("token", auth.accessToken)
        assertEquals("Bearer", auth.tokenType)
        assertEquals(900, auth.expiresIn)
        api.resendRegistration(ResendRegistrationRequest("user@example.com"))

        assertEquals(
            listOf("/api/v1/auth/register", "/api/v1/auth/register/verify", "/api/v1/auth/register/resend"),
            requests.map { it.first }
        )
        val verifyBody = JsonParser.parseString(requests[1].second).asJsonObject
        assertTrue(verifyBody.get("code").asJsonPrimitive.isString)
        assertEquals("004217", verifyBody.get("code").asString)
        assertEquals("user@example.com", verifyBody.get("email").asString)
        val resendBody = JsonParser.parseString(requests[2].second).asJsonObject
        assertEquals(1, resendBody.size())
        assertEquals("user@example.com", resendBody.get("email").asString)
    }
}
