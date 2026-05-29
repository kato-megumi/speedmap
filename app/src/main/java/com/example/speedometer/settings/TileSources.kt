package com.example.speedometer.settings

/**
 * Map style options. With the MapLibre migration each option is described by
 * either a remote style URL (preferred — supports vector tiles) or an inline
 * MapLibre style JSON (used for plain raster XYZ tile sources).
 */
data class TileSourceOption(
    val id: String,
    val label: String,
    val attribution: String,
    /** When non-null, the map loads the style from this URL. */
    val styleUrl: String? = null,
    /** Fallback inline style JSON, used only when [styleUrl] is null. */
    val styleJson: String? = null,
    /**
     * Optional cap on the camera's max zoom. Used for vector styles whose
     * underlying tileset stops at a particular zoom (e.g. GSI vector at
     * z16): allowing the camera to zoom in much further than the source +
     * MapLibre's overscale budget causes the deepest tile to drop, which
     * lets the OSM fallback bleed through and looks wrong inside Japan.
     */
    val cameraMaxZoom: Double? = null
)

object TileSources {

    // GSI Japan vector tiles ("地理院地図Vector") — true vector PBF rendered
    // client-side. Style hosted by GSI's official sample repo.
    val GSI_VECTOR_STD = TileSourceOption(
        id = "gsi_vector_std",
        label = "GSI 標準地図 ベクトル (Japan)",
        attribution = "© 国土地理院 (GSI)",
        styleUrl = "https://gsi-cyberjapan.github.io/gsivectortile-mapbox-gl-js/std.json",
        cameraMaxZoom = 20.0
    )

    val GSI_VECTOR_PALE = TileSourceOption(
        id = "gsi_vector_pale",
        label = "GSI 淡色地図 ベクトル (Japan)",
        attribution = "© 国土地理院 (GSI)",
        styleUrl = "https://gsi-cyberjapan.github.io/gsivectortile-mapbox-gl-js/pale.json",
        cameraMaxZoom = 20.0
    )

    // Legacy GSI raster styles (kept because they're still useful when
    // vector tiles aren't available or look too busy at low zoom).
    val GSI_STD = TileSourceOption(
        id = "gsi_std",
        label = "GSI 標準地図 ラスタ (Japan)",
        attribution = "© 国土地理院 (GSI)",
        styleJson = rasterStyle(
            tiles = listOf("https://cyberjapandata.gsi.go.jp/xyz/std/{z}/{x}/{y}.png"),
            maxZoom = 18,
            attribution = "© GSI Japan"
        )
    )

    val GSI_PHOTO = TileSourceOption(
        id = "gsi_photo",
        label = "GSI Aerial Photo (Japan)",
        attribution = "© 国土地理院 (GSI)",
        styleJson = rasterStyle(
            tiles = listOf("https://cyberjapandata.gsi.go.jp/xyz/seamlessphoto/{z}/{x}/{y}.jpg"),
            maxZoom = 18,
            attribution = "© GSI Japan"
        )
    )

    val OSM = TileSourceOption(
        id = "osm",
        label = "OpenStreetMap",
        attribution = "© OpenStreetMap contributors",
        styleJson = rasterStyle(
            tiles = listOf("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
            maxZoom = 19,
            attribution = "© OpenStreetMap contributors"
        )
    )

    val OPENTOPO = TileSourceOption(
        id = "opentopo",
        label = "OpenTopoMap",
        attribution = "© OpenTopoMap (CC-BY-SA), © OSM",
        styleJson = rasterStyle(
            tiles = listOf(
                "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
                "https://c.tile.opentopomap.org/{z}/{x}/{y}.png"
            ),
            maxZoom = 17,
            attribution = "© OpenTopoMap, © OSM"
        )
    )

    val ALL: List<TileSourceOption> = listOf(
        GSI_VECTOR_STD, GSI_VECTOR_PALE, GSI_STD, GSI_PHOTO, OSM, OPENTOPO
    )

    fun byId(id: String): TileSourceOption = ALL.firstOrNull { it.id == id } ?: GSI_VECTOR_STD
}

/**
 * Build a minimal MapLibre style JSON wrapping a single raster XYZ source.
 */
private fun rasterStyle(
    tiles: List<String>,
    tileSize: Int = 256,
    maxZoom: Int = 19,
    attribution: String
): String {
    val tilesJson = tiles.joinToString(",") { "\"$it\"" }
    return """
    {
      "version": 8,
      "sources": {
        "rastersrc": {
          "type": "raster",
          "tiles": [$tilesJson],
          "tileSize": $tileSize,
          "maxzoom": $maxZoom,
          "attribution": "$attribution"
        }
      },
      "layers": [
        { "id": "background", "type": "background", "paint": { "background-color": "#e8e8e8" } },
        { "id": "rasterlyr", "type": "raster", "source": "rastersrc" }
      ]
    }
    """.trimIndent()
}
