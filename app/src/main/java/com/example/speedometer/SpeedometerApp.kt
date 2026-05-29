package com.example.speedometer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.speedometer.data.AppDatabase
import com.example.speedometer.data.TripRepository
import com.example.speedometer.settings.UserPreferences
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

class SpeedometerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: TripRepository by lazy {
        TripRepository(database.tripDao(), database.locationPointDao())
    }
    val preferences: UserPreferences by lazy { UserPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        // MapLibre needs a one-time init before any MapView is created.
        MapLibre.getInstance(this)
        // Bump the on-disk ambient tile cache from the default (~50 MB)
        // to 500 MB so longer drives keep more terrain cached for
        // instant re-display and offline reuse. Native call is async;
        // failures here are non-fatal — defaults remain in effect.
        OfflineManager.getInstance(this).setMaximumAmbientCacheSize(
            AMBIENT_CACHE_MAX_BYTES,
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() { /* no-op */ }
                override fun onError(message: String) {
                    Log.w("SpeedometerApp", "ambient cache resize failed: $message")
                }
            }
        )
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_TRACKING,
                getString(R.string.channel_tracking_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_tracking_desc) }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_TRACKING = "trip_tracking"
        /** 500 MB ambient tile cache budget. */
        const val AMBIENT_CACHE_MAX_BYTES: Long = 500L * 1024L * 1024L
        fun from(context: Context): SpeedometerApp =
            context.applicationContext as SpeedometerApp
    }
}
