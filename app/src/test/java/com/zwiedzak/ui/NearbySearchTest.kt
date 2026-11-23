package com.zwiedzak.ui

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.zwiedzak.data.network.ApiService
import com.zwiedzak.data.preferences.MapPreferences

class NearbySearchTest {
    @Test
    fun largeRadiusOptionsAreAvailableAndSentInMeters() = runBlocking {
        lateinit var request: Request
        val api = api { request = it }
        for (radius in listOf(100, 200, 300)) {
            assertTrue(radius in MapPreferences.availableRadiiKm)
            searchPlacesFromApi(api, "", "", null, radius * 1_000, null) { 50.0 to 20.0 }
            assertEquals((radius * 1_000).toString(), request.url.queryParameter("radiusMeters"))
        }
    }

    @Test
    fun blankAllSearchUsesCurrentLocationAndConfiguredRadius() = runBlocking {
        lateinit var request: Request
        val api = api { request = it }
        val result = searchPlacesFromApi(api, " ", "", null, 20_000, 1.0 to 2.0) {
            50.1 to 19.9
        }

        assertEquals("/api/v1/places/nearby", request.url.encodedPath)
        assertEquals("50.1", request.url.queryParameter("lat"))
        assertEquals("19.9", request.url.queryParameter("lng"))
        assertEquals("20000", request.url.queryParameter("radiusMeters"))
        assertNull(request.url.queryParameter("mine"))
        assertTrue(result.places.isEmpty())
        assertEquals(50.1, result.userLat, 0.0)
    }

    @Test
    fun mineFilterIsSentForNearbyAndCombinedWithSearchFilters() = runBlocking {
        lateinit var request: Request
        val api = api { request = it }
        searchPlacesFromApi(api, "", "", null, 5_000, null, mine = true) { 50.0 to 20.0 }
        assertEquals("/api/v1/places/nearby", request.url.encodedPath)
        assertEquals("true", request.url.queryParameter("mine"))
        assertEquals("5000", request.url.queryParameter("radiusMeters"))

        for (visited in listOf(true, false)) {
            searchPlacesFromApi(api, "zamek", "", visited, 5_000, null, mine = true) {
                error("Location should not be requested")
            }
            assertEquals("/api/v1/places/search", request.url.encodedPath)
            assertEquals("true", request.url.queryParameter("mine"))
            assertEquals(visited.toString(), request.url.queryParameter("visited"))
        }
    }

    @Test
    fun textAndVisitFiltersUseSearchEndpointWithoutRequestingLocation() = runBlocking {
        lateinit var request: Request
        val api = api { request = it }
        val noLocation: suspend () -> Pair<Double, Double> = { error("Location should not be requested") }

        searchPlacesFromApi(api, "  zamek  ", "", null, 1_000, null, currentLocation = noLocation)
        assertEquals("/api/v1/places/search", request.url.encodedPath)
        assertEquals("zamek", request.url.queryParameter("name"))

        searchPlacesFromApi(api, "", "", false, 1_000, null, currentLocation = noLocation)
        assertEquals("/api/v1/places/search", request.url.encodedPath)
        assertEquals("false", request.url.queryParameter("visited"))
    }

    @Test
    fun nearbyFailureIsNotReportedAsEmptyResults() = runBlocking {
        val api = api(code = 500) {}
        try {
            searchPlacesFromApi(api, "", "", null, 10_000, null) { 50.0 to 20.0 }
            error("Expected HTTP failure")
        } catch (e: retrofit2.HttpException) {
            assertEquals(500, e.code())
        }
    }

    private fun api(code: Int = 200, record: (Request) -> Unit): ApiService {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            record(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body("""{"items":[],"count":0}""".toResponseBody())
                .build()
        }.build()
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
