package com.example.speedometer.location

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton for broadcasting the latest fix and tracking state
 * from the LocationService to UI components.
 */
object LocationBus {
    private val _latestLocation = MutableStateFlow<Location?>(null)
    val latestLocation: StateFlow<Location?> = _latestLocation.asStateFlow()

    private val _activeTripId = MutableStateFlow<Long?>(null)
    val activeTripId: StateFlow<Long?> = _activeTripId.asStateFlow()

    fun publish(location: Location) {
        _latestLocation.value = location
    }

    fun setActiveTrip(id: Long?) {
        _activeTripId.value = id
    }
}
