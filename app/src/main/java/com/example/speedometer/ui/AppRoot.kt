package com.example.speedometer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.speedometer.ui.live.LiveScreen
import com.example.speedometer.ui.settings.SettingsScreen
import com.example.speedometer.ui.tripdetail.TripDetailScreen
import com.example.speedometer.ui.trips.TripListScreen

sealed class Dest(val route: String, val label: String) {
    data object Live : Dest("live", "Live")
    data object Trips : Dest("trips", "Trips")
    data object Settings : Dest("settings", "Settings")
    data object TripDetail : Dest("trip/{id}", "Trip") {
        fun build(id: Long) = "trip/$id"
    }
}

private val bottomTabs = listOf(Dest.Live, Dest.Trips, Dest.Settings)

@Composable
fun AppRoot(onRequestPermissions: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomTabs.map { it.route }) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Dest.Live.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Live.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Live.route) {
                LiveScreen(onRequestPermissions = onRequestPermissions)
            }
            composable(Dest.Trips.route) {
                TripListScreen(onOpen = { id -> nav.navigate(Dest.TripDetail.build(id)) })
            }
            composable(Dest.Settings.route) {
                SettingsScreen()
            }
            composable(Dest.TripDetail.route) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                TripDetailScreen(tripId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun tabIcon(dest: Dest) = when (dest) {
    Dest.Live -> Icons.Filled.Speed
    Dest.Trips -> Icons.AutoMirrored.Filled.List
    Dest.Settings -> Icons.Filled.Settings
    else -> Icons.Filled.Speed
}
