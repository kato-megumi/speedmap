# Speedometer

An Android speedometer that logs fine-grained GPS in the background using a
foreground service, stores trips in Room (SQLite), and plots them on a map
later. Default tiles are the Japan GSI (Geospatial Information Authority of
Japan) standard layer; OSM, OpenTopoMap, GSI pale, and GSI aerial photo are
also selectable in Settings.

## Stack

- Kotlin + Jetpack Compose, Material 3
- Min SDK 29 (Android 10), target / compile SDK 34
- Room + KSP, DataStore Preferences
- FusedLocationProvider + foreground service (type `location`)
- osmdroid for the map (no API key needed)

## Project layout

```
app/src/main/java/com/example/speedometer/
  SpeedometerApp.kt        # Application: DI-by-hand, osmdroid + notif channel
  MainActivity.kt          # Permission flow + Compose host
  data/                    # Room entities, DAOs, repository (incl. haversine)
  location/                # Foreground LocationService + LocationBus
  settings/                # DataStore prefs + tile source catalog
  export/                  # GPX writer + FileProvider share
  util/                    # Distance / duration formatters
  ui/                      # Compose theme, nav, live / trips / detail / settings
```

## Permissions

On first start, the Drive tab requests:

1. `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (+ `POST_NOTIFICATIONS` on 13+)
2. `ACCESS_BACKGROUND_LOCATION` (separate system prompt, required for tracking
   while the screen is off / app is backgrounded)

The foreground service uses `foregroundServiceType="location"` so logging
continues when the app is not in the foreground.

## Building

```powershell
.\gradlew.bat assembleDebug
```

Or open the folder in Android Studio (Koala / Ladybug or later) and let it
sync — the version catalog in `gradle/libs.versions.toml` pins everything.

Run the unit tests (covers `TripMath` haversine + aggregates):

```powershell
.\gradlew.bat test
```

## Notes

- Speed shown in the live view comes straight from the GPS fix
  (`Location.speed`, in m/s, converted by the chosen unit).
- The route on the trip detail map is split into polyline segments colored
  blue → green → red by speed bucket.
- "Minimum displacement" in Settings lets you drop near-duplicate fixes when
  parked.
- GPX export writes to `cacheDir/exports/` and shares via FileProvider with
  authority `${applicationId}.fileprovider`.
