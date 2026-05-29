package com.example.speedometer.ui.map

import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.speedometer.settings.TileSourceOption
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.PropertyFactory

/**
 * Shared MapLibre style helpers used by both the trip detail map and the
 * live mini-map. Centralised here so behaviour stays in lock-step.
 */

/**
 * Load [option] onto [map] and invoke [onLoaded] when the style is ready.
 *
 * Vector styles are loaded directly by URL; we intentionally do not inject
 * an OSM fallback because the GSI vector style has many transparent layers
 * (water, gaps in landuse) which would let the raster basemap show through
 * and look like a double-render inside Japan. If the user wants worldwide
 * raster coverage they can pick the OSM source explicitly.
 *
 * Raster styles use their inline JSON. The fallback empty style keeps
 * MapLibre happy if a future TileSourceOption has neither URL nor JSON.
 */
internal fun applyMapStyle(
    map: MapLibreMap,
    option: TileSourceOption,
    onLoaded: (Style) -> Unit
) {
    if (option.styleUrl != null) {
        // The GSI vector style has 676/774 layers with maxzoom <= 17, so
        // anything past z17 renders as background. Prefetch the JSON and
        // bump every *layer's* maxzoom (NOT the source's — that would
        // make MapLibre request non-existent tiles past z16) so vector
        // layers stay visible at extreme zoom via overzoom of the z16
        // tile. Falls back to plain fromUri on any failure.
        val url = option.styleUrl
        Thread({
            val patched = try {
                fetchAndPatchLayerMaxzoom(url, layerMaxzoom = 22)
            } catch (t: Throwable) {
                Log.w("MapStyleUtils", "Style prefetch failed for $url: ${t.message}")
                null
            }
            Handler(Looper.getMainLooper()).post {
                val builder = if (patched != null) {
                    Style.Builder().fromJson(patched)
                } else {
                    Style.Builder().fromUri(url)
                }
                map.setStyle(builder) { style ->
                    ensureBackgroundLayer(style)
                    applyOverscale(map, style)
                    onLoaded(style)
                    Handler(Looper.getMainLooper()).post { applyOverscale(map, style) }
                }
            }
        }, "style-prefetch").start()
        return
    }
    val builder = when {
        option.styleJson != null -> Style.Builder().fromJson(option.styleJson)
        else -> Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[]}""")
    }
    map.setStyle(builder) { style ->
        ensureBackgroundLayer(style)
        applyOverscale(map, style)
        onLoaded(style)
        Handler(Looper.getMainLooper()).post { applyOverscale(map, style) }
    }
}

/**
 * Fetch the style JSON at [url] and raise every layer's `maxzoom` to at
 * least [layerMaxzoom]. Returns the modified JSON. The source's `maxzoom`
 * is intentionally untouched — bumping it would make MapLibre request
 * tiles past the real maxzoom and 404.
 */
private fun fetchAndPatchLayerMaxzoom(url: String, layerMaxzoom: Int): String {
    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        requestMethod = "GET"
    }
    val raw = conn.inputStream.bufferedReader().use { it.readText() }
    val root = org.json.JSONObject(raw)
    val layers = root.optJSONArray("layers") ?: return raw
    var bumped = 0
    for (i in 0 until layers.length()) {
        val layer = layers.optJSONObject(i) ?: continue
        val current = layer.optInt("maxzoom", Int.MAX_VALUE)
        if (current < layerMaxzoom) {
            layer.put("maxzoom", layerMaxzoom)
            bumped++
        }
    }
    Log.d("MapStyleUtils", "Patched style: bumped $bumped/${layers.length()} layers to maxzoom=$layerMaxzoom")
    return root.toString()
}

/**
 * Set every source's [maxOverscaleFactorForParentTiles] to the uint8_t max
 * (~7 zoom levels of parent overzoom). Without this, MapLibre drops the
 * deepest tile at high camera zoom and renders the background.
 *
 * Skipped if the style is no longer the map's current style (e.g. a tile
 * source switch raced ahead while this call was queued on the main
 * looper) — touching the old Style throws IllegalStateException.
 */
private fun applyOverscale(map: MapLibreMap, style: Style) {
    if (map.style !== style) return
    val sources = try {
        style.sources
    } catch (t: IllegalStateException) {
        // Style was invalidated between the identity check and the call.
        return
    }
    if (sources.isEmpty()) {
        Log.w("MapStyleUtils", "applyOverscale: no sources in style yet")
        return
    }
    for (src in sources) {
        try {
            src.maxOverscaleFactorForParentTiles = 255
        } catch (t: Throwable) {
            Log.w("MapStyleUtils", "overscale set failed on ${src.id}: ${t.message}")
        }
    }
    Log.d("MapStyleUtils", "overscale=255 applied to ${sources.size} sources: " +
        sources.joinToString(",") { it.id })
}

/**
 * Inject a neutral grey background layer below all existing layers so the
 * GL canvas isn't black before tiles paint and gives a sensible "ground"
 * colour for styles like GSI vector that don't define their own
 * background. No-op if already present.
 */
internal fun ensureBackgroundLayer(style: Style) {
    val bgId = "speedometer-bg"
    if (style.getLayer(bgId) != null) return
    val bg = BackgroundLayer(bgId).withProperties(
        PropertyFactory.backgroundColor(AndroidColor.rgb(0xE8, 0xE8, 0xE8))
    )
    val firstLayerId = style.layers.firstOrNull()?.id
    if (firstLayerId != null) style.addLayerBelow(bg, firstLayerId) else style.addLayer(bg)
}
