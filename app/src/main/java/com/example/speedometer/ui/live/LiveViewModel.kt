package com.example.speedometer.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speedometer.SpeedometerApp
import com.example.speedometer.data.TripRepository
import com.example.speedometer.location.GnssBus
import com.example.speedometer.location.GnssStatusSnapshot
import com.example.speedometer.location.LocationBus
import com.example.speedometer.location.LocationService
import com.example.speedometer.settings.SpeedUnit
import com.example.speedometer.settings.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LiveUiState(
    val speedMps: Float = 0f,
    val accuracy: Float = 0f,
    val verticalAccuracy: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val bearingDegrees: Float? = null,
    val hasFix: Boolean = false,
    val gpsTimeMillis: Long? = null,
    val gnss: GnssStatusSnapshot? = null,
    val isTracking: Boolean = false,
    val unit: SpeedUnit = SpeedUnit.KMH,
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Float = 0f,
    val avgSpeedMps: Float = 0f,
    val durationMillis: Long = 0L
)

class LiveViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx: SpeedometerApp = SpeedometerApp.from(app)
    private val prefs: UserPreferences = appCtx.preferences
    private val repo: TripRepository = appCtx.repository

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val activeTripFlow = LocationBus.activeTripId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repo.observeTrip(id)
    }

    val state: StateFlow<LiveUiState> = combine(
        LocationBus.latestLocation,
        LocationBus.activeTripId,
        prefs.unit,
        activeTripFlow,
        GnssBus.status
    ) { loc, tripId, unit, trip, gnss ->
        LiveUiState(
            speedMps = loc?.speed ?: 0f,
            accuracy = loc?.accuracy ?: 0f,
            verticalAccuracy = loc?.takeIf { it.hasVerticalAccuracy() }?.verticalAccuracyMeters,
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            altitudeMeters = loc?.takeIf { it.hasAltitude() }?.altitude,
            bearingDegrees = loc?.takeIf { it.hasBearing() && it.speed > 0.5f }?.bearing,
            hasFix = loc != null,
            gpsTimeMillis = loc?.time?.takeIf { it > 0L },
            gnss = gnss,
            isTracking = tripId != null,
            unit = unit,
            distanceMeters = trip?.distanceMeters ?: 0.0,
            maxSpeedMps = trip?.maxSpeedMps ?: 0f,
            avgSpeedMps = trip?.avgSpeedMps ?: 0f,
            durationMillis = trip?.durationMillis ?: 0L
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveUiState())

    fun start() = LocationService.start(getApplication())
    fun stop() = LocationService.stop(getApplication())
}
