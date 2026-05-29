package com.example.speedometer.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.speedometer.SpeedometerApp
import com.example.speedometer.location.GnssMonitor
import com.example.speedometer.location.LocationPreview
import com.example.speedometer.settings.TileSources
import com.example.speedometer.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveScreen(
    onRequestPermissions: () -> Unit,
    vm: LiveViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Keep a passive GPS reading flowing into LocationBus while the Live
    // screen is on — lets the user see current speed/lat/lon even without
    // a recorded trip. The foreground LocationService (if running) still
    // publishes its own fixes to the same bus; both sources coexist.
    val ctx = LocalContext.current
    val prefs = remember(ctx) { SpeedometerApp.from(ctx).preferences }
    val tileSourceId by prefs.tileSourceId.collectAsStateWithLifecycle(
        initialValue = TileSources.GSI_STD.id
    )
    val liveMapEnabled by prefs.liveMapEnabled.collectAsStateWithLifecycle(
        initialValue = false
    )
    DisposableEffect(Unit) {
        LocationPreview.start(ctx)
        GnssMonitor.start(ctx)
        onDispose {
            LocationPreview.stop()
            GnssMonitor.stop()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.unit.format(state.speedMps),
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(state.unit.label, style = MaterialTheme.typography.titleLarge)
            }
        }

        // Live mini-map: opt-in via Settings → "Show live map". When
        // enabled, shows the current GPS fix as a blue dot and follows
        // it by default; tap the my-location FAB to re-enable follow
        // after panning.
        if (liveMapEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                LiveMiniMap(
                    tileSourceId = tileSourceId,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        StatsCard(state)

        val gnss = state.gnss
        if (gnss != null && gnss.satellitesUsedInFix == 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    "No GPS fix — likely indoors, tunnel, or under heavy obstruction. " +
                        "${gnss.satellitesVisible} sats visible, 0 used in fix.",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.size(8.dp))
        if (state.isTracking) {
            Button(
                onClick = { vm.stop() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) { Text("Stop trip", fontSize = 18.sp) }
        } else {
            Button(
                onClick = {
                    onRequestPermissions()
                    vm.start()
                },
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) { Text("Start trip", fontSize = 18.sp) }
        }

        state.latitude?.let { lat ->
            Text(
                "Fix: %.5f, %.5f (±%.0f m)".format(lat, state.longitude ?: 0.0, state.accuracy),
                style = MaterialTheme.typography.bodySmall
            )
        } ?: Text("Waiting for GPS fix…", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatsCard(state: LiveUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Trip aggregates: only meaningful while a trip is being
            // recorded — hide them when idle so the card focuses on the
            // live GPS readings.
            if (state.isTracking) {
                Row(Modifier.fillMaxWidth()) {
                    Stat("Distance", Formatters.distance(state.distanceMeters), Modifier.weight(1f))
                    Stat("Duration", Formatters.duration(state.durationMillis), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    Stat(
                        "Max",
                        "${state.unit.format(state.maxSpeedMps)} ${state.unit.label}",
                        Modifier.weight(1f)
                    )
                    Stat(
                        "Avg",
                        "${state.unit.format(state.avgSpeedMps)} ${state.unit.label}",
                        Modifier.weight(1f)
                    )
                }
            }
            // Always show live GPS attributes.
            Row(Modifier.fillMaxWidth()) {
                Stat(
                    "Altitude (WGS84)",
                    if (state.altitudeMeters != null) {
                        val base = "%.1f m".format(state.altitudeMeters)
                        state.verticalAccuracy?.let { "$base ±%.1f".format(it) } ?: base
                    } else "—",
                    Modifier.weight(1f)
                )
                Stat(
                    "Accuracy",
                    if (state.hasFix) "±%.1f m".format(state.accuracy) else "—",
                    Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Stat(
                    "GPS signal",
                    state.gnss?.let { g ->
                        "${g.satellitesUsedInFix}/${g.satellitesVisible} sats · %.0f dBHz".format(g.avgCn0DbHz)
                    } ?: "—",
                    Modifier.weight(1f)
                )
                Stat(
                    "GPS time",
                    state.gpsTimeMillis?.let { formatGpsTime(it) } ?: "—",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

private val gpsTimeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

private fun formatGpsTime(millis: Long): String = gpsTimeFormatter.format(Date(millis))

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
