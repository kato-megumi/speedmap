# Add project specific ProGuard rules here.

# MapLibre uses reflection for style/layer plugins.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Room generated implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**

# Kotlin coroutines / kotlinx
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.serialization.**

# Play services location
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# Keep our Room entities (used by Room reflection and GPX export)
-keep class com.example.speedometer.data.** { *; }
