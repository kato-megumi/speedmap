package com.example.speedometer.location

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.speedometer.MainActivity
import com.example.speedometer.R
import com.example.speedometer.SpeedometerApp
import com.example.speedometer.data.LocationPoint
import com.example.speedometer.data.TripRepository
import com.example.speedometer.settings.SpeedUnit
import com.example.speedometer.settings.UserPreferences
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LocationService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var repository: TripRepository
    private lateinit var prefs: UserPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeTripId: Long = -1L
    private var lastSavedLocation: Location? = null
    private var unit: SpeedUnit = SpeedUnit.KMH
    private var minDisplacement: Float = 0f

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            LocationBus.publish(loc)
            handleNewLocation(loc)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = SpeedometerApp.from(this)
        repository = app.repository
        prefs = app.preferences
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking(intent.getStringExtra(EXTRA_TRIP_NAME) ?: defaultTripName())
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startTracking(name: String) {
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }
        startForeground(NOTIF_ID, buildNotification("Starting trip…"))

        scope.launch {
            val intervalMs = prefs.updateIntervalMs.first().toLong().coerceAtLeast(250L)
            minDisplacement = prefs.minDisplacementMeters.first()
            unit = prefs.unit.first()

            val existing = repository.getActiveTrip()
            activeTripId = existing?.id ?: repository.startTrip(name)
            LocationBus.setActiveTrip(activeTripId)

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                .setWaitForAccurateLocation(false)
                .build()

            try {
                fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (se: SecurityException) {
                stopSelf()
            }
        }
    }

    private fun handleNewLocation(loc: Location) {
        if (activeTripId <= 0L) return
        val last = lastSavedLocation
        if (last != null && minDisplacement > 0f && loc.distanceTo(last) < minDisplacement) {
            updateNotification(loc)
            return
        }
        lastSavedLocation = loc
        val point = LocationPoint(
            tripId = activeTripId,
            timestamp = loc.time,
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = if (loc.hasAltitude()) loc.altitude else null,
            speedMps = if (loc.hasSpeed()) loc.speed else 0f,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else 0f
        )
        scope.launch { repository.appendPoint(point) }
        updateNotification(loc)
    }

    private fun updateNotification(loc: Location) {
        val speed = if (loc.hasSpeed()) loc.speed else 0f
        val acc = if (loc.hasAccuracy()) "  ±%.1f m".format(loc.accuracy) else ""
        val text = "${unit.format(speed)} ${unit.label}$acc"
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun stopTracking() {
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        scope.launch {
            if (activeTripId > 0L) repository.stopTrip(activeTripId)
            LocationBus.setActiveTrip(null)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { fused.removeLocationUpdates(callback) } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildNotification(content: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, LocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, SpeedometerApp.CHANNEL_TRACKING)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun defaultTripName(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return "Trip ${fmt.format(java.util.Date())}"
    }

    companion object {
        const val ACTION_START = "com.example.speedometer.START"
        const val ACTION_STOP = "com.example.speedometer.STOP"
        const val EXTRA_TRIP_NAME = "trip_name"
        private const val NOTIF_ID = 42

        fun start(context: android.content.Context, tripName: String? = null) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_START
                if (tripName != null) putExtra(EXTRA_TRIP_NAME, tripName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, LocationService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
