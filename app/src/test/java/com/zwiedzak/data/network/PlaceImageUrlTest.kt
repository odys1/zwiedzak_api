package com.zwiedzak.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceImageUrlTest {
    private val base = "https://example.test/zwiedzak/"
    private val id = "12345678-1234-1234-1234-123456789abc"

    @Test
    fun resolvesApiImageUnderDeploymentPrefix() {
        val image = resolvePlaceImage(base, "/zwiedzak/api/v1/images/$id")!!
        assertEquals("https://example.test/zwiedzak/api/v1/images/$id", image.url)
        assertTrue(image.requiresAuth)
        assertTrue(resolvePlaceImage(base, "api/v1/images/$id")!!.requiresAuth)
    }

    @Test
    fun externalOrOtherPathsNeverReceiveBearerToken() {
        assertFalse(resolvePlaceImage(base, "https://other.test/zwiedzak/api/v1/images/$id")!!.requiresAuth)
        assertFalse(resolvePlaceImage(base, "https://example.test/other/api/v1/images/$id")!!.requiresAuth)
        assertFalse(resolvePlaceImage(base, "http://example.test/zwiedzak/api/v1/images/$id")!!.requiresAuth)
        assertFalse(resolvePlaceImage(base, "/zwiedzak/api/v1/images/$id/extra")!!.requiresAuth)
        assertNull(resolvePlaceImage(base, "file:///private/image.png"))
    }
}
