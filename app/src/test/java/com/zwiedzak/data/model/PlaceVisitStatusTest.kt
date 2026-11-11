package com.zwiedzak.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceVisitStatusTest {

    @Test
    fun parsesVisitStatusAndMarkerColorFromApi() {
        val place = Gson().fromJson(
            """{"id":"123","name":"Muzeum","visitStatus":"avoid","markerColor":"red"}""",
            Place::class.java
        )

        assertEquals(VisitStatus.AVOID, place.visitState)
        assertEquals("red", place.markerColor)
    }

    @Test
    fun missingOrUnknownVisitStatusDefaultsToPlanned() {
        val gson = Gson()
        val missingStatus = gson.fromJson("""{"id":"1","name":"Miejsce"}""", Place::class.java)
        val unknownStatus = gson.fromJson(
            """{"id":"2","name":"Miejsce","visitStatus":"unknown"}""",
            Place::class.java
        )

        assertEquals(VisitStatus.PLANNED, missingStatus.visitState)
        assertEquals(VisitStatus.PLANNED, unknownStatus.visitState)
    }

    @Test
    fun parsesSearchEndpointVisitedFlag() {
        val gson = Gson()
        val visited = gson.fromJson(
            """{"id":"1","name":"Miejsce","isVisited":true}""",
            Place::class.java
        )
        val notVisited = gson.fromJson(
            """{"id":"2","name":"Miejsce","isVisited":false}""",
            Place::class.java
        )

        assertEquals(VisitStatus.VISITED, visited.visitState)
        assertEquals(VisitStatus.PLANNED, notVisited.visitState)
    }

    @Test
    fun parsesPlaceDetailsWithBooleanFeaturesAndImages() {
        val place = Gson().fromJson(
            """{"id":"1","name":"Zamek","hasAccommodation":false,"hasFood":true,"averageRating":4.5,"ratingsCount":3,"images":[{"imageUrl":"https://example.com/zamek.jpg","caption":"Widok"}]}""",
            Place::class.java
        )

        assertEquals(0, place.hasAccommodation)
        assertEquals(1, place.hasFood)
        assertEquals(4.5, place.averageRating!!, 0.0)
        assertEquals(1, place.images?.size)
        assertEquals("https://example.com/zamek.jpg", place.images?.first()?.imageUrl)
        assertEquals("Widok", place.images?.first()?.caption)
    }

    @Test
    fun parsesPlaceImageFromSupportedApiFieldNames() {
        val gson = Gson()
        val camelCaseImageUrl = gson.fromJson(
            """{"id":"1","name":"Miejsce","imageUrl":"https://example.com/place.jpg"}""",
            Place::class.java
        )
        val snakeCaseImageUrl = gson.fromJson(
            """{"id":"2","name":"Miejsce","image_url":"/uploads/place.jpg"}""",
            Place::class.java
        )

        assertEquals("https://example.com/place.jpg", camelCaseImageUrl.imageUrl)
        assertEquals("/uploads/place.jpg", snakeCaseImageUrl.imageUrl)
    }

    @Test
    fun nameSearchMatchesSubstringWithoutCaseOrPolishDiacriticDifferences() {
        val place = Gson().fromJson(
            """{"id":"1","name":"Cisy"}""",
            Place::class.java
        )

        assertEquals(true, place.matchesNameQuery("Ci"))
        assertEquals(true, place.matchesNameQuery("ci"))
        assertEquals(true, place.matchesNameQuery("IS"))
        assertEquals(false, place.matchesNameQuery("Cisyy"))
        assertEquals(true, place.matchesNameQuery(""))
    }

    @Test
    fun addressSearchIsSeparateAndMatchesAddressCityAndVoivodeship() {
        val place = Gson().fromJson(
            """{"id":"1","name":"Zamek Królewski","address":"Plac Zamkowy 4","miasto":"Warszawa","wojewodztwo":"mazowieckie"}""",
            Place::class.java
        )

        assertEquals(true, place.matchesAddressQuery("plac zamkowy"))
        assertEquals(true, place.matchesAddressQuery("warszawa"))
        assertEquals(true, place.matchesAddressQuery("MAZOWIECKIE"))
        assertEquals(false, place.matchesAddressQuery("Kraków"))
        assertEquals(false, place.matchesAddressQuery("zamek"))
        assertEquals(true, place.matchesAddressQuery("  "))
    }
}
