package com.example.speedometer.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.speedometer.data.Trip
import com.example.speedometer.ui.tripdetail.shareGpx
import com.example.speedometer.util.Formatters
import java.text.DateFormat
import java.util.Date

@Composable
fun TripListScreen(
    onOpen: (Long) -> Unit,
    vm: TripListViewModel = viewModel()
) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var renaming by remember { mutableStateOf<Trip?>(null) }
    var deleting by remember { mutableStateOf<Trip?>(null) }
    if (trips.isEmpty()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No trips yet. Start one from the Live tab.")
            OutlinedButton(
                onClick = { vm.createDemoTripToTokyo() },
                modifier = Modifier.padding(top = 16.dp)
            ) { Text("Generate demo trip \u2192 Tokyo") }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedButton(
                onClick = { vm.createDemoTripToTokyo() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Generate demo trip \u2192 Tokyo") }
        }
        items(trips, key = { it.id }) { trip ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(trip.id) }
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(trip.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            DateFormat.getDateTimeInstance().format(Date(trip.startedAt)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${Formatters.distance(trip.distanceMeters)}  •  " +
                                "${Formatters.duration(trip.durationMillis)}  •  " +
                                "${trip.pointCount} samples",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (trip.isActive) {
                            Text("Active", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { renaming = trip }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = {
                        vm.export(context, trip.id) { uri -> shareGpx(context, uri) }
                    }) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Export GPX")
                    }
                    IconButton(onClick = { deleting = trip }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }

    val toRename = renaming
    if (toRename != null) {
        RenameDialog(
            initial = toRename.name,
            onDismiss = { renaming = null },
            onConfirm = { newName ->
                vm.rename(toRename.id, newName)
                renaming = null
            }
        )
    }

    val toDelete = deleting
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete trip?") },
            text = {
                Text(
                    "${toDelete.name} \u2022 ${toDelete.pointCount} samples " +
                        "will be permanently deleted. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(toDelete.id)
                    deleting = null
                }) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename trip") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Trip name") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trim().isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
