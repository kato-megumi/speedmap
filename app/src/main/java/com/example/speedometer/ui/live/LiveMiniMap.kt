package com.example.speedometer.ui.live

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.speedometer.location.LocationBus
import com.example.speedometer.settings.TileSources
import com.example.speedometer.ui.map.applyMapStyle
import com.example.speedometer.ui.map.ensureBackgroundLayer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Compact MapLibre map for the Drive tab showing the current GPS fix as a
 * blue dot. When [follow] is true the camera tracks every new fix; if the
 * user pans the map manually, follow is turned off so they can inspect
 * freely. The "my location" FAB re-enables follow and snaps back to the
 * current fix.
 *
 * [tileSourceId] controls which basemap is used (same options as the trip
 * detail map) so the user sees consistent cartography across the app.
 */
@Composable
fun LiveMiniMap(
    tileSourceId: String,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val option = remember(tileSourceId) { TileSources.byId(tileSourceId) }
    val holder = remember { LiveMapHolder() }
    var follow by remember { mutableStateOf(true) }

    // Stream the latest GPS fix into the map: update the location source
    // (always) and recenter the camera (only when follow is on).
    val latestLocation by LocationBus.latestLocation.collectAsState()
    LaunchedEffect(latestLocation, follow) {
        val loc = latestLocation ?: return@LaunchedEffect
        val style = holder.style ?: return@LaunchedEffect
        val point = Point.fromLngLat(loc.longitude, loc.latitude)
        val src = style.getSourceAs<GeoJsonSource>(LIVE_LOC_SOURCE)
        src?.setGeoJson(FeatureCollection.fromFeatures(arrayOf(Feature.fromGeometry(point))))
        if (follow) {
            val map = holder.map ?: return@LaunchedEffect
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(loc.latitude, loc.longitude),
                    maxOf(map.cameraPosition.zoom, 15.0)
                ),
                400
            )
        }
    }

    // Forward Android lifecycle into MapView so GL contexts/resources behave.
    DisposableEffect(lifecycleOwner, holder) {
        val obs = LifecycleEventObserver { _, e ->
            val mv = holder.view ?: return@LifecycleEventObserver
            when (e) {
                Lifecycle.Event.ON_START -> mv.onStart()
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                Lifecycle.Event.ON_STOP -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            holder.view?.onStop()
            holder.view?.onDestroy()
            holder.view = null
            holder.map = null
            holder.style = null
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).also { mv ->
                    holder.view = mv
                    mv.onCreate(null)
                    mv.getMapAsync { map ->
                        holder.map = map
                        map.uiSettings.apply {
                            isAttributionEnabled = false
                            isLogoEnabled = false
                            isCompassEnabled = false
                        }
                        map.setMaxZoomPreference(option.cameraMaxZoom ?: 20.0)
                        // User-initiated pan disables follow so they can
                        // inspect surroundings without the camera fighting
                        // them on every new fix.
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                follow = false
                            }
                        }
                        applyMapStyle(map, option) { style ->
                            holder.style = style
                            holder.lastTileSourceId = option.id
                            ensureBackgroundLayer(style)
                            installLiveLocationLayers(style)
                            // Initial centering on whatever we already have.
                            LocationBus.latestLocation.value?.let { loc ->
                                map.moveCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(loc.latitude, loc.longitude), 16.0
                                    )
                                )
                            }
                        }
                    }
                }
            },
            update = { _ ->
                val map = holder.map ?: return@AndroidView
                if (holder.lastTileSourceId != option.id) {
                    map.setMaxZoomPreference(option.cameraMaxZoom ?: 20.0)
                    applyMapStyle(map, option) { style ->
                        holder.style = style
                        holder.lastTileSourceId = option.id
                        ensureBackgroundLayer(style)
                        installLiveLocationLayers(style)
                    }
                }
            }
        )

        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Reset bearing & tilt to true-north. Useful after pinch-rotate
            // or 3D-tilt gestures leave the map in an awkward orientation.
            SmallFloatingActionButton(
                onClick = {
                    val map = holder.map ?: return@SmallFloatingActionButton
                    val current = map.cameraPosition
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder(current)
                                .bearing(0.0)
                                .tilt(0.0)
                                .build()
                        ),
                        300
                    )
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = "Reset to north")
            }
            // Snap-to-current-location + re-enable follow. Highlighted while
            // follow is on so the user can see the camera is tracking.
            SmallFloatingActionButton(
                onClick = {
                    follow = true
                    val map = holder.map
                    val loc = LocationBus.latestLocation.value
                    if (map != null && loc != null) {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(loc.latitude, loc.longitude),
                                maxOf(map.cameraPosition.zoom, 16.0)
                            ),
                            400
                        )
                    }
                },
                containerColor = if (follow) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (follow) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Follow location")
            }
        }
    }
}

private const val LIVE_LOC_SOURCE = "live-location-src"
private const val LIVE_LOC_LAYER_HALO = "live-location-halo"
private const val LIVE_LOC_LAYER_DOT = "live-location-dot"

private fun installLiveLocationLayers(style: Style) {
    if (style.getSource(LIVE_LOC_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(LIVE_LOC_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>()))
        )
    }
    if (style.getLayer(LIVE_LOC_LAYER_HALO) == null) {
        style.addLayer(
            CircleLayer(LIVE_LOC_LAYER_HALO, LIVE_LOC_SOURCE).withProperties(
                PropertyFactory.circleRadius(14f),
                PropertyFactory.circleColor(AndroidColor.argb(60, 33, 150, 243)),
                PropertyFactory.circleStrokeWidth(0f)
            )
        )
    }
    if (style.getLayer(LIVE_LOC_LAYER_DOT) == null) {
        style.addLayer(
            CircleLayer(LIVE_LOC_LAYER_DOT, LIVE_LOC_SOURCE).withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(AndroidColor.rgb(33, 150, 243)),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(2f)
            )
        )
    }
}

private class LiveMapHolder {
    var view: MapView? = null
    var map: MapLibreMap? = null
    var style: Style? = null
    var lastTileSourceId: String? = null
}
