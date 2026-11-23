package com.zwiedzak.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceActionModelsTest {
    private val gson = Gson()

    @Test
    fun categoriesResponseReadsDisplayNameAndStableCode() {
        val response = gson.fromJson(
            """{"items":[{"code":"inne","name":"Inne"}]}""",
            CategoriesResponse::class.java
        )

        assertEquals(1, response.items.size)
        assertEquals("inne", response.items.single().code)
        assertEquals("Inne", response.items.single().name)
    }

    @Test
    fun createPlaceRequestUsesDocumentedJsonFieldNames() {
        val request = CreatePlaceRequest(
            name = "Zamek",
            category = "zamek",
            latitude = 52.23,
            longitude = 21.01,
            address = "Warszawa",
            description = null,
            hasAccommodation = false,
            hasFood = true,
            visibility = "public",
            confirmDistinct = true,
            visited = false
        )

        val json = gson.toJsonTree(request).asJsonObject
        assertEquals("Zamek", json.get("name").asString)
        assertEquals("zamek", json.get("category").asString)
        assertEquals(52.23, json.get("latitude").asDouble, 0.0)
        assertEquals(21.01, json.get("longitude").asDouble, 0.0)
        assertFalse(json.get("hasAccommodation").asBoolean)
        assertTrue(json.get("hasFood").asBoolean)
        assertEquals("public", json.get("visibility").asString)
        assertTrue(json.get("confirmDistinct").asBoolean)
        assertFalse(json.get("visited").asBoolean)
        assertTrue(gson.toJsonTree(request.copy(visited = true)).asJsonObject.get("visited").asBoolean)
        val avoid = gson.toJsonTree(request.copy(visited = true, createVisitStatus = "avoid")).asJsonObject
        assertTrue(avoid.get("visited").asBoolean)
        assertEquals("avoid", avoid.get("visitStatus").asString)
        assertFalse(json.has("visitStatus"))
    }

    @Test
    fun visitAndRatingRequestsUseDocumentedFields() {
        assertEquals(
            "avoid",
            gson.toJsonTree(UpdateVisitStatusRequest("avoid"))
                .asJsonObject.get("status").asString
        )
        assertEquals(
            5,
            gson.toJsonTree(SubmitRatingRequest(5)).asJsonObject.get("score").asInt
        )
    }

    @Test
    fun parsesVisitHistoryResponse() {
        val history = gson.fromJson(
            """{"items":[{"id":"v1","visitedAt":"2026-11-16T14:30:25.123456Z"}],"limit":50,"offset":0,"hasMore":false}""",
            PlaceVisitsResponse::class.java
        )
        assertEquals("v1", history.items.single().id)
        assertEquals("2026-11-16T14:30:25.123456Z", history.items.single().visitedAt)
        assertFalse(history.hasMore)
    }
}
