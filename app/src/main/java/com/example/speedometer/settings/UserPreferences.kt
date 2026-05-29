package com.example.speedometer.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SpeedUnit(val label: String, val mpsToUnit: Float) {
    KMH("km/h", 3.6f),
    MPH("mph", 2.23694f),
    MPS("m/s", 1f);

    fun format(mps: Float): String = String.format("%.1f", mps * mpsToUnit)
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {

    private val KEY_UNIT = stringPreferencesKey("speed_unit")
    private val KEY_TILE = stringPreferencesKey("tile_source")
    private val KEY_INTERVAL_MS = intPreferencesKey("update_interval_ms")
    private val KEY_MIN_DISPLACEMENT_M = floatPreferencesKey("min_displacement_m")
    private val KEY_LIVE_MAP = booleanPreferencesKey("live_map_enabled")

    val unit: Flow<SpeedUnit> = context.dataStore.data.map {
        runCatching { SpeedUnit.valueOf(it[KEY_UNIT] ?: SpeedUnit.KMH.name) }
            .getOrDefault(SpeedUnit.KMH)
    }

    val tileSourceId: Flow<String> = context.dataStore.data.map {
        it[KEY_TILE] ?: TileSources.GSI_STD.id
    }

    val updateIntervalMs: Flow<Int> = context.dataStore.data.map {
        it[KEY_INTERVAL_MS] ?: 1000
    }

    val minDisplacementMeters: Flow<Float> = context.dataStore.data.map {
        it[KEY_MIN_DISPLACEMENT_M] ?: 5f
    }

    // Live mini-map on the Drive tab. Off by default because rendering a
    // MapLibre surface on the main screen has a real GPU cost and not all
    // users care for it while driving.
    val liveMapEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_LIVE_MAP] ?: false
    }

    suspend fun setUnit(unit: SpeedUnit) {
        context.dataStore.edit { it[KEY_UNIT] = unit.name }
    }

    suspend fun setTileSource(id: String) {
        context.dataStore.edit { it[KEY_TILE] = id }
    }

    suspend fun setUpdateIntervalMs(ms: Int) {
        context.dataStore.edit { it[KEY_INTERVAL_MS] = ms }
    }

    suspend fun setMinDisplacementMeters(m: Float) {
        context.dataStore.edit { it[KEY_MIN_DISPLACEMENT_M] = m }
    }

    suspend fun setLiveMapEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LIVE_MAP] = enabled }
    }
}
