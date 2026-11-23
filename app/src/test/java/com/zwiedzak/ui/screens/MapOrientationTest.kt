package com.zwiedzak.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapOrientationTest {
    @Test
    fun compassClicksAlternateNorthAndDirection() {
        var next = MapOrientationMode.NORTH
        assertEquals(MapOrientationMode.NORTH, next)
        next = next.next()
        assertEquals(MapOrientationMode.DIRECTION, next)
        assertEquals(MapOrientationMode.NORTH, next.next())
    }

    @Test
    fun movingUsesGpsBearingRatherThanCompass() {
        assertEquals(270f, directionHeading(270f, 3f, 60f, 5f)!!, 0.001f)
        assertEquals(270f, directionHeading(270f, 0.2f, 60f, 5f)!!, 0.001f)
        assertEquals(5f, directionHeading(365f, 1f, 60f, 0f)!!, 0.001f)
    }

    @Test
    fun stationaryOrMissingGpsUsesTrueCompassHeading() {
        assertEquals(5f, directionHeading(270f, 0f, 355f, 10f)!!, 0.001f)
        assertEquals(350f, directionHeading(null, null, 5f, -15f)!!, 0.001f)
        assertNull(directionHeading(null, null, null, 0f))
    }
}
