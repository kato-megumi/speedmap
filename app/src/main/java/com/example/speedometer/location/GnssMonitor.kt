package com.example.speedometer.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Ref-counted singleton that registers a [GnssStatus.Callback] on the system
 * [LocationManager] and publishes summary snapshots to [GnssBus]. Mirrors the
 * lifecycle pattern used by [LocationPreview] so a Composable can simply call
 * [start] in `DisposableEffect` and [stop] in `onDispose`.
 */
object GnssMonitor {
    private var manager: LocationManager? = null
    private var refCount: Int = 0

    private val callback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val count = status.satelliteCount
            var used = 0
            var sumCn0 = 0f
            var maxCn0 = 0f
            for (i in 0 until count) {
                if (status.usedInFix(i)) {
                    used += 1
                    val cn0 = status.getCn0DbHz(i).toFloat()
                    sumCn0 += cn0
                    if (cn0 > maxCn0) maxCn0 = cn0
                }
            }
            val avg = if (used > 0) sumCn0 / used else 0f
            GnssBus.publish(
                GnssStatusSnapshot(
                    satellitesUsedInFix = used,
                    satellitesVisible = count,
                    avgCn0DbHz = avg,
                    maxCn0DbHz = maxCn0
                )
            )
        }

        override fun onStopped() {
            GnssBus.clear()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(context: Context) {
        if (!hasPermission(context)) return
        refCount += 1
        if (refCount > 1) return
        val lm = context.applicationContext
            .getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        manager = lm
        try {
            lm.registerGnssStatusCallback(callback, null)
        } catch (_: SecurityException) {
            refCount -= 1
            manager = null
        }
    }

    @Synchronized
    fun stop() {
        if (refCount == 0) return
        refCount -= 1
        if (refCount > 0) return
        manager?.unregisterGnssStatusCallback(callback)
        manager = null
        GnssBus.clear()
    }

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
