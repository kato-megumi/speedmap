package com.example.speedometer.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Lightweight in-process location listener for UI surfaces that want a live
 * GPS reading without recording a trip (e.g. the Live screen idle state, or
 * the trip-detail "current location" marker).
 *
 * Publishes to [LocationBus] just like [LocationService] does, so the rest of
 * the app keeps observing a single source of truth. When the foreground
 * [LocationService] is also running, both sources just feed the same bus.
 */
object LocationPreview {
    private var fused: FusedLocationProviderClient? = null
    private var refCount: Int = 0

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { LocationBus.publish(it) }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(context: Context) {
        if (!hasPermission(context)) return
        refCount += 1
        if (refCount > 1) return
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        fused = client
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permission revoked between check and request — treat as no-op.
            refCount -= 1
            fused = null
        }
    }

    @Synchronized
    fun stop() {
        if (refCount == 0) return
        refCount -= 1
        if (refCount > 0) return
        fused?.let {
            try { it.removeLocationUpdates(callback) } catch (_: Exception) {}
        }
        fused = null
    }

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
