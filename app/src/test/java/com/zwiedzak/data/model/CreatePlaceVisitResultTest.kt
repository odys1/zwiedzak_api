package com.zwiedzak.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatePlaceVisitResultTest {
    @Test
    fun avoidRequiresExplicitStatusAndVisitDate() {
        assertTrue(isCreatedAvoidVisitConfirmed(
            """{"visitStatus":"avoid","lastVisitedAt":"2026-11-23T12:00:00Z"}"""
        ))
        for (body in listOf(
            null,
            "invalid",
            """{"visitStatus":"visited","lastVisitedAt":"2026-11-23T12:00:00Z"}""",
            """{"visitStatus":"avoid","lastVisitedAt":null}""",
            """{"visitStatus":"avoid","lastVisitedAt":""}"""
        )) {
            assertFalse(isCreatedAvoidVisitConfirmed(body))
        }
    }
}
