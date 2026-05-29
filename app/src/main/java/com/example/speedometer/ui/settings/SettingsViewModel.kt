package com.example.speedometer.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speedometer.SpeedometerApp
import com.example.speedometer.settings.SpeedUnit
import com.example.speedometer.settings.TileSources
import com.example.speedometer.settings.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val unit: SpeedUnit = SpeedUnit.KMH,
    val tileSourceId: String = TileSources.GSI_STD.id,
    val intervalMs: Int = 1000,
    val minDisplacementM: Float = 5f,
    val liveMapEnabled: Boolean = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs: UserPreferences = SpeedometerApp.from(app).preferences

    val state: StateFlow<SettingsUiState> = combine(
        prefs.unit,
        prefs.tileSourceId,
        prefs.updateIntervalMs,
        prefs.minDisplacementMeters,
        prefs.liveMapEnabled
    ) { unit, tile, interval, disp, liveMap ->
        SettingsUiState(unit, tile, interval, disp, liveMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setUnit(u: SpeedUnit) = viewModelScope.launch { prefs.setUnit(u) }
    fun setTile(id: String) = viewModelScope.launch { prefs.setTileSource(id) }
    fun setInterval(ms: Int) = viewModelScope.launch { prefs.setUpdateIntervalMs(ms) }
    fun setMinDisplacement(m: Float) = viewModelScope.launch { prefs.setMinDisplacementMeters(m) }
    fun setLiveMapEnabled(v: Boolean) = viewModelScope.launch { prefs.setLiveMapEnabled(v) }
}
