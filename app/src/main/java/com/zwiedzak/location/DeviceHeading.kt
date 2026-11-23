package com.zwiedzak.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun observeDeviceHeading(context: Context): Flow<Float> = callbackFlow {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (sensor == null) {
        close(IllegalStateException("Kompas urządzenia jest niedostępny."))
        return@callbackFlow
    }
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val heading = deviceAzimuth(event, windowManager) ?: return
            trySend(heading)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
        close(IllegalStateException("Nie udało się uruchomić kompasu urządzenia."))
    }
    awaitClose { manager.unregisterListener(listener) }
}

private fun deviceAzimuth(event: SensorEvent, windowManager: WindowManager): Float? {
    val matrix = FloatArray(9)
    val adjusted = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(matrix, event.values)
    @Suppress("DEPRECATION")
    val rotation = windowManager.defaultDisplay.rotation
    val axes = when (rotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }
    if (!SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, adjusted)) return null
    val orientation = FloatArray(3)
    SensorManager.getOrientation(adjusted, orientation)
    return (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
}
