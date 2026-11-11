package com.zwiedzak.data.preferences

import android.content.Context

object MapPreferences {
    const val DEFAULT_RADIUS_KM = 10
    val availableRadiiKm = listOf(1, 2, 5, 10, 20, 50)

    private const val PREFERENCES_NAME = "zwiedzak_map_prefs"
    private const val KEY_RADIUS_KM = "radius_km"

    fun getRadiusKm(context: Context): Int {
        val savedValue = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).getInt(KEY_RADIUS_KM, DEFAULT_RADIUS_KM)
        return savedValue.takeIf { it in availableRadiiKm } ?: DEFAULT_RADIUS_KM
    }

    fun setRadiusKm(context: Context, radiusKm: Int) {
        require(radiusKm in availableRadiiKm)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RADIUS_KM, radiusKm)
            .apply()
    }
}
