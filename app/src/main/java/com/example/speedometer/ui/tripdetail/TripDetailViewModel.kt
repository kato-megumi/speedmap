package com.example.speedometer.ui.tripdetail

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speedometer.SpeedometerApp
import com.example.speedometer.data.LocationPoint
import com.example.speedometer.data.Trip
import com.example.speedometer.export.GpxExporter
import com.example.speedometer.settings.SpeedUnit
import com.example.speedometer.settings.TileSources
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripDetailUiState(
    val trip: Trip? = null,
    val points: List<LocationPoint> = emptyList(),
    val unit: SpeedUnit = SpeedUnit.KMH,
    val tileSourceId: String = TileSources.GSI_STD.id
)

class TripDetailViewModel(app: Application, tripId: Long) : AndroidViewModel(app) {
    private val appCtx = SpeedometerApp.from(app)
    private val repo = appCtx.repository
    private val prefs = appCtx.preferences

    val state: StateFlow<TripDetailUiState> = combine(
        repo.observeTrip(tripId),
        repo.observePoints(tripId),
        prefs.unit,
        prefs.tileSourceId
    ) { trip, points, unit, tile ->
        TripDetailUiState(trip, points, unit, tile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripDetailUiState())

    fun export(context: Context, onReady: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            val s = state.value
            val trip = s.trip ?: return@launch
            val uri = GpxExporter.export(context, trip, s.points)
            onReady(uri)
        }
    }
}

fun shareGpx(context: Context, uri: android.net.Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share GPX"))
}
