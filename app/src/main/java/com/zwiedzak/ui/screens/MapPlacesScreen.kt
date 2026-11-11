package com.zwiedzak.ui.screens

import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.VisitStatus
import com.zwiedzak.data.preferences.MapPreferences
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPlacesScreen(
    places: List<Place>,
    isMockData: Boolean,
    centerLatitude: Double,
    centerLongitude: Double,
    radiusKm: Int,
    showSearchResults: Boolean,
    onRadiusChange: (Int) -> Unit,
    onCenterChange: (Double, Double) -> Unit,
    onPlaceClick: (Place) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val lastRequestedCenter = remember {
        mutableStateOf(GeoPoint(centerLatitude, centerLongitude))
    }
    val lastFittedRadiusKm = remember { mutableStateOf(radiusKm) }
    val lastSearchResultsKey = remember { mutableStateOf("") }
    val latestCenterChange by rememberUpdatedState(onCenterChange)
    val latestPlaceClick by rememberUpdatedState(onPlaceClick)
    val latestCenterLatitude by rememberUpdatedState(centerLatitude)
    val latestCenterLongitude by rememberUpdatedState(centerLongitude)
    val latestRadiusKm by rememberUpdatedState(radiusKm)
    var showDiameterMenu by remember { mutableStateOf(false) }
    val visiblePlaces = remember(places, centerLatitude, centerLongitude, radiusKm, showSearchResults) {
        if (showSearchResults) {
            places
        } else {
            places.filter { place ->
                val latitude = place.latitude ?: return@filter false
                val longitude = place.longitude ?: return@filter false
                distanceMeters(centerLatitude, centerLongitude, latitude, longitude) <= radiusKm * 1000.0
            }
        }
    }
    val searchResultsKey = remember(places, showSearchResults) {
        if (showSearchResults) {
            places.joinToString("|") { "${it.id}:${it.latitude}:${it.longitude}" }
        } else {
            ""
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                mapViewState.value?.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                mapViewState.value?.onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewState.value?.onPause()
            mapViewState.value = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (showSearchResults) "Wyniki wyszukiwania" else "Mapa miejsc")
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Powrót"
                        )
                    }
                },
                actions = {
                    if (!showSearchResults) {
                        Box {
                            TextButton(onClick = { showDiameterMenu = true }) {
                                Text("Promień $radiusKm km")
                            }
                            DropdownMenu(
                                expanded = showDiameterMenu,
                                onDismissRequest = { showDiameterMenu = false }
                            ) {
                                MapPreferences.availableRadiiKm.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (option == radiusKm) {
                                                    "$option km · wybrano"
                                                } else {
                                                    "$option km"
                                                }
                                            )
                                        },
                                        onClick = {
                                            showDiameterMenu = false
                                            onRadiusChange(option)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val cacheDirectory = File(context.cacheDir, "osmdroid")
                    Configuration.getInstance().apply {
                        userAgentValue = context.packageName
                        osmdroidBasePath = cacheDirectory
                        osmdroidTileCache = File(cacheDirectory, "tiles")
                    }

                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(13.0)
                        controller.setCenter(GeoPoint(centerLatitude, centerLongitude))
                        overlays.add(
                            MapEventsOverlay(
                                object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                                        latestCenterChange(point.latitude, point.longitude)
                                        return true
                                    }

                                    override fun longPressHelper(point: GeoPoint): Boolean {
                                        latestCenterChange(point.latitude, point.longitude)
                                        return true
                                    }
                                }
                            )
                        )
                        onResume()
                        mapViewState.value = this
                        post {
                            if (showSearchResults) {
                                fitPlacesInMap(this, places)
                                lastSearchResultsKey.value = searchResultsKey
                            } else {
                                fitCircleInMap(
                                    this,
                                    latestCenterLatitude,
                                    latestCenterLongitude,
                                    latestRadiusKm
                                )
                            }
                        }
                    }
                },
                update = { mapView ->
                    val centerChanged =
                        lastRequestedCenter.value.latitude != centerLatitude ||
                            lastRequestedCenter.value.longitude != centerLongitude
                    val radiusChanged = lastFittedRadiusKm.value != radiusKm
                    val searchResultsChanged =
                        showSearchResults && lastSearchResultsKey.value != searchResultsKey
                    if (showSearchResults && searchResultsChanged) {
                        mapView.post { fitPlacesInMap(mapView, places) }
                        lastSearchResultsKey.value = searchResultsKey
                    } else if (!showSearchResults && (centerChanged || radiusChanged)) {
                        mapView.post {
                            fitCircleInMap(
                                mapView,
                                centerLatitude,
                                centerLongitude,
                                radiusKm
                            )
                        }
                        lastRequestedCenter.value = GeoPoint(centerLatitude, centerLongitude)
                        lastFittedRadiusKm.value = radiusKm
                    }
                    mapView.overlays.removeAll { it is Marker || it is Polygon }
                    if (!showSearchResults) {
                        val center = GeoPoint(centerLatitude, centerLongitude)
                        mapView.overlays.add(
                            Polygon().apply {
                                points = circlePoints(center, radiusKm * 1000.0)
                                fillColor = android.graphics.Color.TRANSPARENT
                                strokeColor = android.graphics.Color.rgb(33, 150, 243)
                                strokeWidth = 2f
                                title = "Promień: $radiusKm km"
                            }
                        )
                    }

                    visiblePlaces.forEach { place ->
                        val latitude = place.latitude ?: return@forEach
                        val longitude = place.longitude ?: return@forEach

                        val marker = Marker(mapView).apply {
                            position = GeoPoint(latitude, longitude)
                            title = place.name
                            subDescription = place.visitState.label
                            icon = statusMarkerDrawable(context, place)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setOnMarkerClickListener { _, _ ->
                                latestPlaceClick(place)
                                true
                            }
                        }
                        mapView.overlays.add(marker)
                    }
                    mapView.invalidate()
                }
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.small,
                shadowElevation = 2.dp
            ) {
                Text(
                    text = buildString {
                        if (isMockData) append("Dane demonstracyjne · ")
                        if (showSearchResults) {
                            append("${visiblePlaces.size} wyników wyszukiwania")
                        } else {
                            append("${visiblePlaces.size}/${places.size} miejsc · promień $radiusKm km · dotknij mapy, aby zmienić środek")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = "© OpenStreetMap contributors",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun fitCircleInMap(
    mapView: MapView,
    latitude: Double,
    longitude: Double,
    radiusKm: Int
) {
    val circleBounds = BoundingBox.fromGeoPoints(
        circlePoints(GeoPoint(latitude, longitude), radiusKm * 1000.0)
    )
    val padding = (24 * mapView.context.resources.displayMetrics.density).toInt()
    mapView.zoomToBoundingBox(circleBounds, false, padding)
}

private fun fitPlacesInMap(mapView: MapView, places: List<Place>) {
    val points = places.mapNotNull { place ->
        val latitude = place.latitude ?: return@mapNotNull null
        val longitude = place.longitude ?: return@mapNotNull null
        GeoPoint(latitude, longitude)
    }
    if (points.isEmpty()) return

    val padding = (48 * mapView.context.resources.displayMetrics.density).toInt()
    if (points.size == 1) {
        mapView.controller.setCenter(points.first())
        mapView.controller.setZoom(13.5)
        return
    }

    val bounds = BoundingBox.fromGeoPoints(points)
    if (bounds.latNorth == bounds.latSouth && bounds.lonEast == bounds.lonWest) {
        mapView.controller.setCenter(points.first())
        mapView.controller.setZoom(13.5)
    } else {
        mapView.zoomToBoundingBox(bounds, false, padding)
    }
}

private fun statusMarkerDrawable(
    context: android.content.Context,
    place: Place
): GradientDrawable {
    val fallbackColor = when (place.visitState) {
        VisitStatus.PLANNED -> android.graphics.Color.GRAY
        VisitStatus.VISITED -> android.graphics.Color.rgb(46, 125, 50)
        VisitStatus.AVOID -> android.graphics.Color.rgb(198, 40, 40)
    }
    val markerColor = place.markerColor?.let {
        runCatching { android.graphics.Color.parseColor(it) }.getOrNull()
    } ?: fallbackColor
    val diameter = (18 * context.resources.displayMetrics.density).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(markerColor)
        setStroke((2 * context.resources.displayMetrics.density).toInt(), android.graphics.Color.WHITE)
        setSize(diameter, diameter)
    }
}

private fun circlePoints(center: GeoPoint, radiusMeters: Double): List<GeoPoint> {
    val earthRadiusMeters = 6_371_000.0
    val angularRadius = radiusMeters / earthRadiusMeters
    val centerLatitude = Math.toRadians(center.latitude)
    val centerLongitude = Math.toRadians(center.longitude)

    return (0..360).map { bearingDegrees ->
        val bearing = Math.toRadians(bearingDegrees.toDouble())
        val latitude = kotlin.math.asin(
            sin(centerLatitude) * cos(angularRadius) +
                cos(centerLatitude) * sin(angularRadius) * cos(bearing)
        )
        val longitude = centerLongitude + atan2(
            sin(bearing) * sin(angularRadius) * cos(centerLatitude),
            cos(angularRadius) - sin(centerLatitude) * sin(latitude)
        )
        GeoPoint(Math.toDegrees(latitude), Math.toDegrees(longitude))
    }
}

private fun distanceMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDifference = Math.toRadians(toLatitude - fromLatitude)
    val longitudeDifference = Math.toRadians(toLongitude - fromLongitude)
    val haversine = sin(latitudeDifference / 2) * sin(latitudeDifference / 2) +
        cos(Math.toRadians(fromLatitude)) * cos(Math.toRadians(toLatitude)) *
        sin(longitudeDifference / 2) * sin(longitudeDifference / 2)
    return 2 * earthRadiusMeters * atan2(sqrt(haversine), sqrt(1 - haversine))
}
