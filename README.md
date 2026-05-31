# Speedometer (speedmap)

An Android driving companion that streams GPS in a foreground service, logs
trips to a local Room database, and reviews them on a MapLibre map. Built
with Jetpack Compose + Material 3.

The default basemap is the **Japan GSI vector tile** style (rendered fully
on-device by MapLibre Native), with OSM raster, OpenTopoMap, GSI raster,
GSI pale, and GSI aerial photo also selectable in Settings.

## Highlights

- **Live tab** — large speedometer plus an optional embedded mini-map
  that follows your fix, with North / center-on-location controls.
- **Trip recording** — fine-grained GPS captured in a foreground service so
  logging continues while the screen is off or the app is backgrounded.
- **Trip review** — route polyline coloured by speed bucket *or* elevation
  bucket, with on-map FABs for North, center-on-current-fix, and fit-to-route.
- **Map tile cache** — 500 MB MapLibre ambient cache so revisited terrain
  loads instantly and works offline; live usage + clear button in Settings.
- **GPX export** — share a single trip as `.gpx` via Android's share sheet.
- **Stats** — distance, duration, average / max speed, altitude profile via
  the elevation colour mode + legend.

## Stack

- Kotlin 2.0.20 + Jetpack Compose (BOM 2024.09.00), Material 3
- Min SDK 29 (Android 10), target / compile SDK 34, JVM 17, AGP 8.5.2
- MapLibre Native 11.5.0 (`org.maplibre.gl:android-sdk`)
- Room 2.6.1 + KSP 2.0.20-1.0.25, DataStore Preferences 1.1.1
- Google Play Services Location 21.3.0 (FusedLocationProvider)

## Screenshots

> _Placeholder — drop `docs/screenshots/{live,trips,detail,settings}.png`
> into the repo and reference them here._

| Live | Trips | Trip detail | Settings |
| ----- | ----- | ----------- | -------- |
|       |       |             |          |

## Project layout

```
app/src/main/java/com/example/speedometer/
  SpeedometerApp.kt        # Application: DI-by-hand, MapLibre + cache + notif
  MainActivity.kt          # Permission flow + Compose host
  data/                    # Room entities, DAOs, repository (incl. haversine)
  location/                # Foreground LocationService + LocationBus
  settings/                # DataStore prefs + tile source catalog
  export/                  # GPX writer + FileProvider share
  util/                    # Distance / duration formatters
  ui/
    live/                  # Live tab + LiveMiniMap composable
    trips/                 # Trip list
    tripdetail/            # Map + stats + colour-mode legend
    settings/              # Unit, tile source, live-map toggle, cache controls
    map/                   # MapStyleUtils (shared GSI layer-maxzoom patch)
```

## Permissions

On first start, the Live tab requests:

1. `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`
   (+ `POST_NOTIFICATIONS` on Android 13+)
2. `ACCESS_BACKGROUND_LOCATION` (separate system prompt; required for
   tracking while the screen is off / app is backgrounded)

The foreground service uses `foregroundServiceType="location"` so logging
continues when the app is not in the foreground.

## Map notes

- **GSI vector style** has many transparent layers (water, gaps in
  landuse), so the app explicitly does **not** inject an OSM raster
  fallback under it — that would produce a visible double-render inside
  Japan. Use the OSM tile source explicitly if you want worldwide raster
  coverage.
- The default GSI vector style ships with most layers capped at
  `maxzoom ≤ 17`, which renders as a solid background past z17. The app
  fetches the style JSON at runtime and bumps every layer's `maxzoom` to
  22, while leaving the **source's** `maxzoom` at its real value (16) so
  MapLibre doesn't 404-request non-existent tiles.
- `maxOverscaleFactorForParentTiles = 255` is applied to every source for
  the maximum allowed parent-tile overzoom.

## Building

```powershell
.\gradlew.bat assembleDebug
# or:
.\gradlew.bat installDebug
```

Or open the folder in Android Studio (Koala / Ladybug or later) and let it
sync — the version catalog in `gradle/libs.versions.toml` pins everything.

Run the unit tests (covers `TripMath` haversine + aggregates):

```powershell
.\gradlew.bat test
```

## Implementation notes

- Speed shown in the live view comes straight from the GPS fix
  (`Location.speed`, in m/s, converted by the chosen unit).
- "Minimum displacement" in Settings lets you drop near-duplicate fixes
  when parked.
- GPX export writes to `cacheDir/exports/` and shares via FileProvider
  with authority `${applicationId}.fileprovider`.
- Tile cache lives at `cacheDir/cache.db`; the size shown in Settings sums
  it with `filesDir/mbgl-offline.db`.

## License

MIT — see [`LICENSE`](LICENSE).

