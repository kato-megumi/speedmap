package com.example.speedometer.ui.trips

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speedometer.SpeedometerApp
import com.example.speedometer.data.Trip
import com.example.speedometer.debug.DemoTripGenerator
import com.example.speedometer.export.GpxExporter
import com.example.speedometer.location.LocationBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SpeedometerApp.from(app).repository

    val trips: StateFlow<List<Trip>> = repo.observeTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) = viewModelScope.launch { repo.deleteTrip(id) }

    fun rename(id: Long, newName: String) = viewModelScope.launch {
        repo.renameTrip(id, newName.trim().ifEmpty { return@launch })
    }

    fun export(context: Context, tripId: Long, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val trip = repo.observeTrip(tripId).first() ?: return@launch
            val points = repo.observePoints(tripId).first()
            val uri = GpxExporter.export(context, trip, points)
            onReady(uri)
        }
    }

    /**
     * Generate a fake completed trip from the current GPS location (or a
     * sensible fallback) to Tokyo Station, for testing the map UI.
     */
    fun createDemoTripToTokyo() {
        viewModelScope.launch {
            val last = LocationBus.latestLocation.value
            val startLat = last?.latitude ?: 34.6937   // Osaka fallback
            val startLon = last?.longitude ?: 135.5023
            val tokyoLat = 35.6812
            val tokyoLon = 139.7671
            val points = DemoTripGenerator.generate(
                startLat = startLat,
                startLon = startLon,
                endLat = tokyoLat,
                endLon = tokyoLon,
                sampleCount = 250,
                avgSpeedMps = 75.0 // ~270 km/h shinkansen-ish
            )
            repo.insertSyntheticTrip("Demo \u2192 Tokyo", points)
        }
    }
}
