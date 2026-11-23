package com.zwiedzak.data.model

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EditPlaceResultTest {
    @Test
    fun usesReturnedCopyIdAndKeepsOriginalForSharedEdits() {
        assertEquals(
            EditPlaceResult("copy", true),
            parseEditPlaceResult("""{"id":"copy","originalId":"original","copied":true}""", "original")
        )
        assertEquals(
            EditPlaceResult("original", false),
            parseEditPlaceResult("""{"id":"original","copied":false}""", "original")
        )
    }

    @Test
    fun rejectsMissingOrInconsistentResult() {
        listOf(
            """{"copied":true}""",
            """{"id":"original","copied":true}""",
            """{"id":"copy","copied":false}""",
            """{"id":"copy","copied":"true"}"""
        ).forEach { body ->
            assertThrows(IOException::class.java) { parseEditPlaceResult(body, "original") }
        }
    }
}
