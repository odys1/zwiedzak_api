package com.zwiedzak.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditPlacePayloadTest {
    private val request = CreatePlaceRequest(
        "Zamek", "zamek", 50.06, 19.94, null, null,
        false, true, "public", false, true
    )
    private val place = Place(
        id = "p", name = "Zamek", category = "zamek", latitude = 50.06,
        longitude = 19.94, address = "Adres", description = "Opis", hasFood = 1
    )

    @Test
    fun personalEditSendsChangedFieldsAndExplicitNulls() {
        val json = editPlacePayload(place, request)
        assertTrue(json.get("address").isJsonNull)
        assertTrue(json.get("description").isJsonNull)
        assertTrue(json.get("visited").asBoolean)
        assertFalse(json.has("visibility"))
        assertFalse(json.has("applyToShared"))
    }

    @Test
    fun editingAvoidOrVisitedDoesNotRecordAnotherVisitOrChangeStatus() {
        for (status in listOf("avoid", "visited")) {
            val original = place.copy(visitStatus = status)
            val json = editPlacePayload(original, request)
            assertFalse(json.has("visited"))
            assertTrue(json.get("address").isJsonNull)
        }
    }

    @Test
    fun uncheckingVisitedSendsPlannedAndUnchangedFormStillAddsToMyList() {
        val original = place.copy(visitStatus = "avoid")
        val unchecked = editPlacePayload(original, request.copy(visited = false))
        assertFalse(unchecked.get("visited").asBoolean)
        val same = editPlacePayload(original, request.copy(address = "Adres", description = "Opis"))
        assertEquals(1, same.size())
        assertEquals("Zamek", same.get("name").asString)
        assertFalse(same.has("visited"))
    }

    @Test
    fun editingAvoidSendsOnlyChangedVisitStatus() {
        val selected = editPlacePayload(place, request.copy(editVisitStatus = VisitStatus.AVOID))
        assertEquals("avoid", selected.get("visitStatus").asString)
        assertFalse(selected.has("visited"))

        val avoided = place.copy(visitStatus = "avoid")
        val unchanged = editPlacePayload(avoided, request.copy(editVisitStatus = VisitStatus.AVOID))
        assertFalse(unchanged.has("visitStatus"))
        assertFalse(unchanged.has("visited"))
        val removed = editPlacePayload(avoided, request.copy(editVisitStatus = VisitStatus.VISITED))
        assertEquals("visited", removed.get("visitStatus").asString)
        assertFalse(removed.has("visited"))
        val unvisited = editPlacePayload(avoided, request.copy(editVisitStatus = VisitStatus.PLANNED))
        assertEquals("planned", unvisited.get("visitStatus").asString)
        val visited = editPlacePayload(place, request.copy(editVisitStatus = VisitStatus.VISITED))
        assertTrue(visited.get("visited").asBoolean)
        assertFalse(visited.has("visitStatus"))
    }
}
