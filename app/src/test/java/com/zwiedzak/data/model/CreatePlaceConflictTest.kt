package com.zwiedzak.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreatePlaceConflictTest {
    @Test
    fun parsesDuplicateAndNearbyCandidates() {
        for (error in listOf("POSSIBLE_DUPLICATES", "NEARBY_PLACES_REQUIRE_CONFIRMATION")) {
            val candidates = parseCreatePlaceConflict(
                """{"error":"$error","candidates":[{"name":"Zamek","address":"Rynek","distanceMeters":27}]}"""
            )
            assertEquals(listOf(DuplicatePlace("Zamek", "Rynek", 27)), candidates)
        }
    }

    @Test
    fun malformedOrUnrelatedConflictsCannotAuthorizeDuplicates() {
        for (body in listOf(
            "not json",
            """{"error":"OTHER","candidates":[{"name":"Zamek"}]}""",
            """{"error":"POSSIBLE_DUPLICATES","candidates":[]}""",
            """{"error":"POSSIBLE_DUPLICATES","candidates":[{"name":null}]}"""
        )) {
            assertNull(parseCreatePlaceConflict(body))
        }
    }
}
