package com.zwiedzak.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceActionModelsTest {
    private val gson = Gson()

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
            confirmDistinct = true
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
    }

    @Test
    fun visitPhotoAndRatingRequestsUseDocumentedFields() {
        assertEquals(
            "avoid",
            gson.toJsonTree(UpdateVisitStatusRequest("avoid"))
                .asJsonObject.get("status").asString
        )
        val image = gson.toJsonTree(
            AddPlaceImageRequest("https://example.com/image.jpg", "Widok")
        ).asJsonObject
        assertEquals("https://example.com/image.jpg", image.get("imageUrl").asString)
        assertEquals("Widok", image.get("caption").asString)
        assertEquals(
            5,
            gson.toJsonTree(SubmitRatingRequest(5)).asJsonObject.get("score").asInt
        )
    }
}
