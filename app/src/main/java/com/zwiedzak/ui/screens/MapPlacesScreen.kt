package com.zwiedzak.ui.screens

import android.Manifest
import android.graphics.drawable.GradientDrawable
import android.hardware.GeomagneticField
import android.location.Location
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zwiedzak.data.model.Place
import com.zwiedzak.data.model.VisitStatus
import com.zwiedzak.data.preferences.MapPreferences
import com.zwiedzak.location.LocationTracker
import com.zwiedzak.location.observeDeviceHeading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    apiErrorMessage: String?,
    centerLatitude: Double,
    centerLongitude: Double,
    userLatitude: Double?,
    userLongitude: Double?,
    radiusKm: Int,
    showSearchResults: Boolean,
    onRadiusChange: (Int) -> Unit,
    onCenterChange: (Double, Double) -> Unit,
    onUserLocationChange: (Double, Double) -> Unit,
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
    val latestUserLocationChange by rememberUpdatedState(onUserLocationChange)
    val latestPlaceClick by rememberUpdatedState(onPlaceClick)
    val latestCenterLatitude by rememberUpdatedState(centerLatitude)
    val latestCenterLongitude by rememberUpdatedState(centerLongitude)
    val latestRadiusKm by rememberUpdatedState(radiusKm)
    var showDiameterMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val locationTracker = remember(context) { LocationTracker(context.applicationContext) }
    var refreshRequest by remember { mutableStateOf(0) }
    var localUserPosition by remember { mutableStateOf<GeoPoint?>(null) }
    val lastLocation = remember { mutableStateOf<Location?>(null) }
    val following = remember { mutableStateOf(true) }
    val compassRotation = remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableStateOf(MapOrientationMode.NORTH) }
    var nextMode by remember { mutableStateOf(MapOrientationMode.NORTH) }
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshRequest++
    }

    LaunchedEffect(mapViewState.value, refreshRequest) {
        val map = mapViewState.value ?: return@LaunchedEffect
        val location = try {
            if (locationTracker.hasLocationPermission()) locationTracker.getFreshLocation() else null
        } catch (error: SecurityException) {
            snackbarHostState.showSnackbar("Brak uprawnienia do lokalizacji. Włącz je w ustawieniach aplikacji.")
            return@LaunchedEffect
        }
        if (location != null) {
            val point = GeoPoint(location.latitude, location.longitude)
            lastLocation.value = location
            localUserPosition = point
            latestUserLocationChange(location.latitude, location.longitude)
            if (!showSearchResults) latestCenterChange(location.latitude, location.longitude)
            map.controller.setCenter(point)
        } else {
            snackbarHostState.showSnackbar(
                if (locationTracker.hasLocationPermission()) {
                    "Nie udało się ustalić aktualnej lokalizacji."
                } else {
                    "Brak uprawnienia do lokalizacji. Naciśnij kompas, aby je przyznać."
                }
            )
        }
    }
    LaunchedEffect(mapViewState.value, mode, isResumed, refreshRequest) {
        val map = mapViewState.value ?: return@LaunchedEffect
        if (mode != MapOrientationMode.DIRECTION || !isResumed || !locationTracker.hasLocationPermission()) {
            return@LaunchedEffect
        }
        coroutineScope {
            var magneticHeading: Float? = null
            fun updateOrientation() {
                if (mode != MapOrientationMode.DIRECTION) return
                val location = lastLocation.value
                val declination = location?.let {
                    GeomagneticField(
                        it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), System.currentTimeMillis()
                    ).declination
                } ?: 0f
                val heading = directionHeading(
                    location?.takeIf { it.hasBearing() }?.bearing,
                    location?.takeIf { it.hasSpeed() }?.speed,
                    magneticHeading,
                    declination
                ) ?: return
                map.mapOrientation = -heading
                compassRotation.floatValue = -heading
            }
            launch {
                try {
                    locationTracker.observeLocation().collect { location ->
                        lastLocation.value = location
                        val point = GeoPoint(location.latitude, location.longitude)
                        localUserPosition = point
                        latestUserLocationChange(location.latitude, location.longitude)
                        if (following.value) map.controller.setCenter(point)
                        updateOrientation()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar("Nie udało się śledzić lokalizacji: ${error.localizedMessage ?: "błąd GPS"}.")
                }
            }
            launch {
                try {
                    observeDeviceHeading(context).collect { heading ->
                        magneticHeading = heading
                        updateOrientation()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    snackbarHostState.showSnackbar("Kompas niedostępny. Kierunek będzie dostępny podczas ruchu z GPS.")
                }
            }
        }
    }
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
                isResumed = true
                mapViewState.value?.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                isResumed = false
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    IconButton(onClick = {
                        mode = nextMode
                        nextMode = nextMode.next()
                        following.value = true
                        if (mode == MapOrientationMode.NORTH) {
                            mapViewState.value?.mapOrientation = 0f
                            compassRotation.floatValue = 0f
                        }
                        if (locationTracker.hasLocationPermission()) {
                            refreshRequest++
                        } else {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    }) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = if (nextMode == MapOrientationMode.NORTH) {
                                "Wyśrodkuj i ustaw północ na górze"
                            } else {
                                "Wyśrodkuj i śledź kierunek ruchu"
                            },
                            modifier = Modifier.graphicsLayer { rotationZ = compassRotation.floatValue },
                            tint = if (mode == MapOrientationMode.DIRECTION && following.value) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
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
                        var touchX = 0f
                        var touchY = 0f
                        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                        setOnTouchListener { _, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    touchX = event.x
                                    touchY = event.y
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    if (kotlin.math.abs(event.x - touchX) > touchSlop ||
                                        kotlin.math.abs(event.y - touchY) > touchSlop) {
                                        following.value = false
                                    }
                                }
                            }
                            false
                        }
                        overlays.add(
                            MapEventsOverlay(
                                object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                                        following.value = false
                                        latestCenterChange(point.latitude, point.longitude)
                                        return true
                                    }

                                    override fun longPressHelper(point: GeoPoint): Boolean {
                                        following.value = false
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
                    val userPosition = localUserPosition ?: if (userLatitude != null && userLongitude != null) {
                        GeoPoint(userLatitude, userLongitude)
                    } else {
                        null
                    }
                    if (userPosition != null) {
                        mapView.overlays.add(
                            Marker(mapView).apply {
                                position = userPosition
                                title = "Twoja pozycja"
                                icon = userLocationMarkerDrawable(context)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                        )
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
                        apiErrorMessage?.let { append("Błąd API: $it · ") }
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

private fun userLocationMarkerDrawable(context: android.content.Context): GradientDrawable {
    val density = context.resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(android.graphics.Color.rgb(25, 118, 210))
        setStroke((3 * density).toInt(), android.graphics.Color.WHITE)
        setSize((22 * density).toInt(), (22 * density).toInt())
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
