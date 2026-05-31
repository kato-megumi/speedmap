package com.example.speedometer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.speedometer.settings.SpeedUnit
import com.example.speedometer.settings.TileSources
import com.example.speedometer.SpeedometerApp
import java.io.File
import kotlinx.coroutines.launch
import org.maplibre.android.offline.OfflineManager

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var cacheBytes by remember { mutableStateOf(currentMapCacheBytes(ctx)) }
    var cacheRefreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(cacheRefreshTick) {
        cacheBytes = currentMapCacheBytes(ctx)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Section("Speed unit") {
            SpeedUnit.values().forEach { u ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(state.unit == u) { vm.setUnit(u) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = state.unit == u, onClick = { vm.setUnit(u) })
                    Text(u.label, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Section("Map tiles") {
            TileSources.ALL.forEach { opt ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(state.tileSourceId == opt.id) { vm.setTile(opt.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.tileSourceId == opt.id,
                        onClick = { vm.setTile(opt.id) }
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(opt.label)
                        Text(opt.attribution, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Section("Update interval: ${state.intervalMs} ms") {
            Slider(
                value = state.intervalMs.toFloat(),
                onValueChange = { vm.setInterval(it.toInt()) },
                valueRange = 250f..10_000f,
                steps = 38
            )
        }

        Section("Minimum displacement: ${"%.0f".format(state.minDisplacementM)} m") {
            Slider(
                value = state.minDisplacementM,
                onValueChange = { vm.setMinDisplacement(it) },
                valueRange = 0f..50f,
                steps = 49
            )
            Text(
                "Skip logging points closer than this to the previous one.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Section("Live screen") {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show live map")
                    Text(
                        "Embed a small map on the Live tab that follows " +
                            "your current location.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = state.liveMapEnabled,
                    onCheckedChange = { vm.setLiveMapEnabled(it) }
                )
            }
        }

        Section("Map cache") {
            Text(
                "MapLibre stores downloaded tiles on disk so revisited " +
                    "areas load instantly and work offline.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Using ${formatMb(cacheBytes)} of " +
                    "${formatMb(SpeedometerApp.AMBIENT_CACHE_MAX_BYTES)} " +
                    "budget",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = {
                    OfflineManager.getInstance(ctx).clearAmbientCache(
                        object : OfflineManager.FileSourceCallback {
                            override fun onSuccess() {
                                cacheRefreshTick++
                                scope.launch {
                                    snackbarHost.showSnackbar("Map cache cleared")
                                }
                            }
                            override fun onError(message: String) {
                                scope.launch {
                                    snackbarHost.showSnackbar("Clear failed: $message")
                                }
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear map cache") }
        }

        SnackbarHost(hostState = snackbarHost)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/**
 * Sum the on-disk size of MapLibre's ambient cache files. Paths are
 * implementation details of MapLibre Android but are stable in 11.x:
 * tile cache lives at `cacheDir/cache.db` and the offline-region index
 * at `filesDir/mbgl-offline.db`.
 */
private fun currentMapCacheBytes(ctx: android.content.Context): Long {
    val files = listOf(
        File(ctx.cacheDir, "cache.db"),
        File(ctx.cacheDir, "cache.db-journal"),
        File(ctx.cacheDir, "cache.db-wal"),
        File(ctx.filesDir, "mbgl-offline.db"),
    )
    return files.sumOf { if (it.exists()) it.length() else 0L }
}

private fun formatMb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 100) String.format("%.0f MB", mb)
    else String.format("%.1f MB", mb)
}
