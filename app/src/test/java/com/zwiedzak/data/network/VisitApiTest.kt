package com.zwiedzak.data.network

import com.sun.net.httpserver.HttpServer
import com.zwiedzak.data.model.CreatePlaceRequest
import com.zwiedzak.data.model.UpdateVisitStatusRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitApiTest {
    @Test
    fun writesAreNotRetriedAfter503OrRedirect() = runBlocking {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            exchange.requestBody.use { it.readBytes() }
            if (exchange.requestURI.path.endsWith("/me")) {
                exchange.responseHeaders.add("Retry-After", "0")
                exchange.sendResponseHeaders(503, -1)
            } else {
                exchange.responseHeaders.add("Location", "/redirected")
                exchange.sendResponseHeaders(307, -1)
            }
            exchange.close()
        }
        val originalUrl = RetrofitInstance.getBaseUrl()
        server.start()
        try {
            RetrofitInstance.setBaseUrl("http://127.0.0.1:${server.address.port}/")
            val api = RetrofitInstance.nonRetryingApi
            val statusResponse = api.updateVisitStatus("place", UpdateVisitStatusRequest("visited"))
            assertEquals(503, statusResponse.code())
            assertEquals(1, requests.get())

            val createResponse = api.createPlace(
                CreatePlaceRequest(
                    name = "Miejsce",
                    category = "category",
                    latitude = 52.0,
                    longitude = 21.0,
                    address = null,
                    description = null,
                    hasAccommodation = false,
                    hasFood = false,
                    visibility = "private",
                    confirmDistinct = false,
                    visited = true
                )
            )
            assertEquals(307, createResponse.code())
            assertEquals(2, requests.get())
            val editResponse = api.editPlace(
                "place",
                """{"visited":true}""".toRequestBody("application/json".toMediaType())
            )
            assertEquals(307, editResponse.code())
            assertEquals(3, requests.get())
        } finally {
            RetrofitInstance.setBaseUrl(originalUrl)
            server.stop(0)
        }
    }
}
