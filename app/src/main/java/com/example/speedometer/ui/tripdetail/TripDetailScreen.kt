package com.example.speedometer.ui.tripdetail

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.speedometer.SpeedometerApp
import com.example.speedometer.data.LocationPoint
import com.example.speedometer.location.LocationBus
import com.example.speedometer.location.LocationPreview
import com.example.speedometer.settings.TileSourceOption
import com.example.speedometer.settings.TileSources
import com.example.speedometer.ui.map.applyMapStyle
import com.example.speedometer.ui.map.ensureBackgroundLayer
import com.example.speedometer.util.Formatters
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(tripId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: TripDetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TripDetailViewModel(SpeedometerApp.from(context), tripId) as T
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        val currentLocation by LocationBus.latestLocation.collectAsState()
        val ctx = LocalContext.current
        DisposableEffect(Unit) {
            LocationPreview.start(ctx)
            onDispose { LocationPreview.stop() }
        }
        val mapHolder = remember { TripMapHolder() }
        var colorMode by remember { mutableStateOf(RouteColorMode.Speed) }
        val maxSpeedSafe = maxOf(state.trip?.maxSpeedMps ?: 0f, 1f)
        val altRange = remember(state.points) {
            val withAlt = state.points.mapNotNull { it.altitude }
            if (withAlt.isEmpty()) 0.0 to 1.0
            else withAlt.min() to withAlt.max()
        }
        val hasAltitude = state.points.any { it.altitude != null }
        val colorScheme = remember(colorMode, maxSpeedSafe, altRange) {
            RouteColorScheme(
                mode = colorMode,
                maxSpeedMps = maxSpeedSafe,
                minAlt = altRange.first,
                maxAlt = altRange.second
            )
        }
        TripMap(
            holder = mapHolder,
            points = state.points,
            tileSourceId = state.tileSourceId,
            currentLat = currentLocation?.latitude,
            currentLon = currentLocation?.longitude,
            colorScheme = colorScheme,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        )

        TopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ),
            title = {
                Text(
                    state.trip?.name ?: "Trip",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsGrid(state)
                if (hasAltitude) {
                    ColorModeToggle(
                        mode = colorMode,
                        onChange = { colorMode = it }
                    )
                }
                when (colorMode) {
                    RouteColorMode.Speed -> SpeedLegend(
                        unit = state.unit,
                        maxMps = maxSpeedSafe
                    )
                    RouteColorMode.Elevation -> AltitudeLegend(
                        minAlt = altRange.first,
                        maxAlt = altRange.second
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 260.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Reset map bearing/tilt to true-north.
            SmallFloatingActionButton(
                onClick = { mapHolder.resetNorth() }
            ) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = "Reset to north"
                )
            }
            // Jump the camera to the live GPS fix. Useful for comparing
            // where you are now relative to the past trip route.
            SmallFloatingActionButton(
                onClick = {
                    val loc = currentLocation ?: return@SmallFloatingActionButton
                    mapHolder.centerOn(loc.latitude, loc.longitude)
                }
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = "Center on current location"
                )
            }
            SmallFloatingActionButton(
                onClick = { mapHolder.fitToBounds(state.points) }
            ) {
                Icon(
                    Icons.Filled.CenterFocusStrong,
                    contentDescription = "Recenter on route"
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(state: TripDetailUiState) {
    val trip = state.trip ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Stat("Distance", Formatters.distance(trip.distanceMeters), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Stat("Duration", Formatters.duration(trip.durationMillis), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Stat(
                "Max",
                "${state.unit.format(trip.maxSpeedMps)} ${state.unit.label}",
                Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Stat(
                "Avg",
                "${state.unit.format(trip.avgSpeedMps)} ${state.unit.label}",
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// MapLibre map
// =========================================================================

private const val ROUTE_SOURCE = "route-src"
private const val ROUTE_CASING_LAYER = "route-casing-layer"
private const val ROUTE_LAYER = "route-layer"
private const val ENDPOINTS_SOURCE = "endpoints-src"
private const val START_LAYER = "endpoint-start-layer"
private const val END_LAYER = "endpoint-end-layer"
private const val CURRENT_SOURCE = "current-src"
private const val CURRENT_LAYER = "current-layer"

internal enum class RouteColorMode { Speed, Elevation }

/** Resolved color + bucket assignment for the current route-color mode. */
private class RouteColorScheme(
    val mode: RouteColorMode,
    val maxSpeedMps: Float,
    val minAlt: Double,
    val maxAlt: Double
) {
    fun colorFor(p: LocationPoint): Int = when (mode) {
        RouteColorMode.Speed -> colorForSpeed(p.speedMps, maxSpeedMps)
        RouteColorMode.Elevation -> colorForAltitude(p.altitude ?: minAlt, minAlt, maxAlt)
    }
    /** Color for a segment between two points — uses midpoint speed/altitude for smooth blending. */
    fun colorFor(a: LocationPoint, b: LocationPoint): Int = when (mode) {
        RouteColorMode.Speed -> colorForSpeed((a.speedMps + b.speedMps) / 2f, maxSpeedMps)
        RouteColorMode.Elevation -> {
            val midAlt = ((a.altitude ?: minAlt) + (b.altitude ?: minAlt)) / 2.0
            colorForAltitude(midAlt, minAlt, maxAlt)
        }
    }
    fun bucketOf(p: LocationPoint): Int = when (mode) {
        RouteColorMode.Speed -> speedBucket(p.speedMps, maxSpeedMps)
        RouteColorMode.Elevation -> altitudeBucket(p.altitude ?: minAlt, minAlt, maxAlt)
    }
    // Composite key so a holder can detect mode/range changes and rebuild.
    val signature: String = "$mode|$maxSpeedMps|$minAlt|$maxAlt"
}

@Composable
private fun TripMap(
    holder: TripMapHolder,
    points: List<LocationPoint>,
    tileSourceId: String,
    currentLat: Double?,
    currentLon: Double?,
    colorScheme: RouteColorScheme,
    modifier: Modifier = Modifier
) {
    val option: TileSourceOption = remember(tileSourceId) { TileSources.byId(tileSourceId) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).also { mv ->
                mv.onCreate(null)
                mv.onStart()
                mv.onResume()
                mv.getMapAsync { map ->
                    holder.map = map
                    map.uiSettings.isAttributionEnabled = true
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.isLogoEnabled = false
                    map.setMaxZoomPreference(option.cameraMaxZoom ?: 20.0)
                    map.setMinZoomPreference(2.0)
                    applyMapStyle(map, option) { style ->
                        holder.style = style
                        holder.lastTileSourceId = option.id
                        // Initial layers — route is empty until points arrive.
                        installRouteLayers(style)
                        installEndpointLayers(style)
                        installCurrentLayer(style)
                        holder.refreshRoute(points, colorScheme)
                        holder.refreshEndpoints(points)
                        holder.refreshCurrent(currentLat, currentLon)
                        holder.maybeInitialCenter(points, currentLat, currentLon)
                    }
                }
            }
        },
        update = { mv ->
            val map = holder.map ?: return@AndroidView
            // Re-apply style only if the user actually switched it.
            if (holder.lastTileSourceId != option.id) {
                map.setMaxZoomPreference(option.cameraMaxZoom ?: 20.0)
                applyMapStyle(map, option) { style ->
                    holder.style = style
                    holder.lastTileSourceId = option.id
                    ensureBackgroundLayer(style)
                    installRouteLayers(style)
                    installEndpointLayers(style)
                    installCurrentLayer(style)
                    holder.refreshRoute(points, colorScheme)
                    holder.refreshEndpoints(points)
                    holder.refreshCurrent(currentLat, currentLon)
                }
                return@AndroidView
            }
            holder.refreshRoute(points, colorScheme)
            holder.refreshEndpoints(points)
            holder.refreshCurrent(currentLat, currentLon)
            holder.maybeInitialCenter(points, currentLat, currentLon)
        }
    )
    DisposableEffect(Unit) {
        onDispose {
            holder.map = null
            holder.style = null
        }
    }
}

private fun installRouteLayers(style: Style) {
    if (style.getSource(ROUTE_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(
                ROUTE_SOURCE,
                FeatureCollection.fromFeatures(emptyArray<Feature>()),
                GeoJsonOptions().withTolerance(0f)
            )
        )
    }
    // Dark border/casing beneath the colored route for visibility.
    if (style.getLayer(ROUTE_CASING_LAYER) == null) {
        style.addLayer(
            LineLayer(ROUTE_CASING_LAYER, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineColor(AndroidColor.rgb(30, 30, 30)),
                PropertyFactory.lineOpacity(0.7f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        )
    }
    if (style.getLayer(ROUTE_LAYER) == null) {
        style.addLayer(
            LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineColor(Expression.get("color")),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
        )
    }
}

private fun installEndpointLayers(style: Style) {
    if (style.getSource(ENDPOINTS_SOURCE) == null) {
        style.addSource(GeoJsonSource(ENDPOINTS_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>())))
    }
    if (style.getLayer(START_LAYER) == null) {
        style.addLayer(
            CircleLayer(START_LAYER, ENDPOINTS_SOURCE).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(AndroidColor.rgb(46, 204, 113)),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(2f)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("start")))
        )
    }
    if (style.getLayer(END_LAYER) == null) {
        style.addLayer(
            CircleLayer(END_LAYER, ENDPOINTS_SOURCE).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(AndroidColor.rgb(231, 76, 60)),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(2f)
            ).withFilter(Expression.eq(Expression.get("kind"), Expression.literal("end")))
        )
    }
}

private fun installCurrentLayer(style: Style) {
    if (style.getSource(CURRENT_SOURCE) == null) {
        style.addSource(GeoJsonSource(CURRENT_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>())))
    }
    if (style.getLayer(CURRENT_LAYER) == null) {
        style.addLayer(
            CircleLayer(CURRENT_LAYER, CURRENT_SOURCE).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(AndroidColor.rgb(52, 152, 219)),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(2f)
            )
        )
    }
}

private class TripMapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null
    var didInitialCenter: Boolean = false
    var lastTileSourceId: String? = null
    var lastPointsCount: Int = -1
    var lastSchemeSignature: String? = null
    var lastCurrentLat: Double? = null
    var lastCurrentLon: Double? = null

    fun refreshRoute(points: List<LocationPoint>, scheme: RouteColorScheme) {
        val style = style ?: return
        if (lastPointsCount == points.size && lastSchemeSignature == scheme.signature) return
        lastPointsCount = points.size
        lastSchemeSignature = scheme.signature
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: return
        if (points.size < 2) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
            return
        }
        val features = ArrayList<Feature>(points.size - 1)
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val color = scheme.colorFor(a, b)
            val seg = listOf(
                Point.fromLngLat(a.longitude, a.latitude),
                Point.fromLngLat(b.longitude, b.latitude)
            )
            features.add(
                Feature.fromGeometry(LineString.fromLngLats(seg)).apply {
                    addStringProperty("color", colorHex(color))
                }
            )
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun refreshEndpoints(points: List<LocationPoint>) {
        val style = style ?: return
        val source = style.getSourceAs<GeoJsonSource>(ENDPOINTS_SOURCE) ?: return
        if (points.isEmpty()) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
            return
        }
        val start = Feature.fromGeometry(
            Point.fromLngLat(points.first().longitude, points.first().latitude)
        ).apply { addStringProperty("kind", "start") }
        val end = Feature.fromGeometry(
            Point.fromLngLat(points.last().longitude, points.last().latitude)
        ).apply { addStringProperty("kind", "end") }
        source.setGeoJson(FeatureCollection.fromFeatures(arrayOf(start, end)))
    }

    fun refreshCurrent(lat: Double?, lon: Double?) {
        val style = style ?: return
        val source = style.getSourceAs<GeoJsonSource>(CURRENT_SOURCE) ?: return
        if (lat == null || lon == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
            return
        }
        if (lat == lastCurrentLat && lon == lastCurrentLon) return
        lastCurrentLat = lat
        lastCurrentLon = lon
        source.setGeoJson(
            FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(Point.fromLngLat(lon, lat))))
        )
    }

    fun maybeInitialCenter(points: List<LocationPoint>, currentLat: Double?, currentLon: Double?) {
        if (didInitialCenter) return
        val map = map ?: return
        if (points.isNotEmpty()) {
            val bbox = buildBounds(points) ?: return
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bbox, 80))
            didInitialCenter = true
        } else if (currentLat != null && currentLon != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLat, currentLon), 15.0))
            didInitialCenter = true
        }
    }

    fun fitToBounds(points: List<LocationPoint>) {
        val map = map ?: return
        val bbox = buildBounds(points) ?: return
        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bbox, 80))
    }

    /** Animate to a specific lat/lon, preserving zoom but at least 15.0. */
    fun centerOn(lat: Double, lon: Double) {
        val map = map ?: return
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(lat, lon),
                maxOf(map.cameraPosition.zoom, 15.0)
            ),
            400
        )
    }

    /** Animate camera bearing and tilt back to true-north / flat. */
    fun resetNorth() {
        val map = map ?: return
        val current = map.cameraPosition
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder(current)
                    .bearing(0.0)
                    .tilt(0.0)
                    .build()
            ),
            300
        )
    }

    private fun buildBounds(points: List<LocationPoint>): LatLngBounds? {
        if (points.isEmpty()) return null
        var minLat = points[0].latitude; var maxLat = minLat
        var minLon = points[0].longitude; var maxLon = minLon
        for (p in points) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }
        if (minLat == maxLat && minLon == maxLon) {
            // Degenerate single point: build a tiny bbox so the camera move
            // doesn't divide by zero.
            val d = 0.0005
            return LatLngBounds.Builder()
                .include(LatLng(minLat - d, minLon - d))
                .include(LatLng(maxLat + d, maxLon + d))
                .build()
        }
        return LatLngBounds.Builder()
            .include(LatLng(minLat, minLon))
            .include(LatLng(maxLat, maxLon))
            .build()
    }
}

private fun speedBucket(mps: Float, maxMps: Float): Int {
    val t = (mps / maxMps).coerceIn(0f, 1f)
    return (t * 8f).toInt().coerceIn(0, 8)
}

@Composable
private fun ColorModeToggle(mode: RouteColorMode, onChange: (RouteColorMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = mode == RouteColorMode.Speed,
            onClick = { onChange(RouteColorMode.Speed) },
            label = { Text("Color: Speed") }
        )
        FilterChip(
            selected = mode == RouteColorMode.Elevation,
            onClick = { onChange(RouteColorMode.Elevation) },
            label = { Text("Color: Elevation") }
        )
    }
}

@Composable
private fun AltitudeLegend(minAlt: Double, maxAlt: Double) {
    val gradientStops = 32
    val colors = (0 until gradientStops).map { i ->
        val alt = minAlt + (i.toDouble() / (gradientStops - 1)) * (maxAlt - minAlt)
        Color(colorForAltitude(alt, minAlt, maxAlt))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(colors))
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "${minAlt.toInt()} m",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${((minAlt + maxAlt) / 2.0).toInt()} m",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "${maxAlt.toInt()} m",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

@Composable
private fun SpeedLegend(
    unit: com.example.speedometer.settings.SpeedUnit,
    maxMps: Float
) {
    val gradientStops = 32
    val colors = (0 until gradientStops).map { i ->
        val mps = (i.toFloat() / (gradientStops - 1)) * maxMps
        Color(colorForSpeed(mps, maxMps))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(colors))
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "${unit.format(0f)} ${unit.label}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                unit.format(maxMps / 2f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "${unit.format(maxMps)} ${unit.label}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

private fun colorForSpeed(mps: Float, maxMps: Float): Int {
    val t = (mps / maxMps).coerceIn(0f, 1f)
    val r = (t * 255).toInt()
    val g = ((1f - kotlin.math.abs(t - 0.5f) * 2f).coerceIn(0f, 1f) * 200).toInt()
    val b = ((1f - t) * 255).toInt()
    return AndroidColor.rgb(r, g, b)
}

/**
 * Elevation colormap: dark green (low) → light green → yellow → orange → red (high).
 */
private fun colorForAltitude(alt: Double, minAlt: Double, maxAlt: Double): Int {
    val range = (maxAlt - minAlt).coerceAtLeast(1.0)
    val t = ((alt - minAlt) / range).coerceIn(0.0, 1.0).toFloat()
    return when {
        t < 0.25f -> {
            val u = t / 0.25f
            // dark green (27,94,32) -> light green (104,159,56)
            AndroidColor.rgb(
                lerp(27, 104, u), lerp(94, 159, u), lerp(32, 56, u)
            )
        }
        t < 0.50f -> {
            val u = (t - 0.25f) / 0.25f
            // light green (104,159,56) -> yellow (251,192,45)
            AndroidColor.rgb(
                lerp(104, 251, u), lerp(159, 192, u), lerp(56, 45, u)
            )
        }
        t < 0.75f -> {
            val u = (t - 0.50f) / 0.25f
            // yellow (251,192,45) -> orange (230,124,25)
            AndroidColor.rgb(
                lerp(251, 230, u), lerp(192, 124, u), lerp(45, 25, u)
            )
        }
        else -> {
            val u = (t - 0.75f) / 0.25f
            // orange (230,124,25) -> red (198,40,40)
            AndroidColor.rgb(
                lerp(230, 198, u), lerp(124, 40, u), lerp(25, 40, u)
            )
        }
    }
}

private fun lerp(a: Int, b: Int, t: Float): Int =
    (a + (b - a) * t).toInt().coerceIn(0, 255)

private fun altitudeBucket(alt: Double, minAlt: Double, maxAlt: Double): Int {
    val range = (maxAlt - minAlt).coerceAtLeast(1.0)
    val t = ((alt - minAlt) / range).coerceIn(0.0, 1.0)
    return (t * 8.0).toInt().coerceIn(0, 8)
}

private fun colorHex(c: Int): String =
    "#%02x%02x%02x".format(AndroidColor.red(c), AndroidColor.green(c), AndroidColor.blue(c))

