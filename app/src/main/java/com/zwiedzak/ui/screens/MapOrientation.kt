package com.zwiedzak.ui.screens

internal enum class MapOrientationMode {
    NORTH,
    DIRECTION;

    fun next(): MapOrientationMode =
        if (this == NORTH) DIRECTION else NORTH
}

internal fun directionHeading(
    bearing: Float?,
    speed: Float?,
    magneticHeading: Float?,
    declination: Float
): Float? {
    val heading = if (bearing != null && bearing.isFinite() &&
        speed != null && speed.isFinite() && speed > 0f
    ) {
        bearing
    } else {
        magneticHeading?.takeIf { it.isFinite() }?.plus(declination)
    } ?: return null
    return ((heading % 360f) + 360f) % 360f
}
