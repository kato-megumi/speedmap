package com.example.speedometer.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the current GNSS constellation health. Updated whenever the
 * system delivers a fresh GnssStatus to [GnssMonitor].
 *
 * - [satellitesUsedInFix]: number of satellites the receiver actually used to
 *   compute the most recent fix (the meaningful "signal strength" indicator).
 * - [satellitesVisible]: total satellites the receiver can see, used or not.
 * - [avgCn0DbHz] / [maxCn0DbHz]: carrier-to-noise density for the satellites
 *   used in fix. Higher = stronger signal. Typical clear-sky values are
 *   30–45 dBHz; <25 dBHz is weak (indoors, tunnel, dense foliage).
 */
data class GnssStatusSnapshot(
    val satellitesUsedInFix: Int,
    val satellitesVisible: Int,
    val avgCn0DbHz: Float,
    val maxCn0DbHz: Float
)

object GnssBus {
    private val _status = MutableStateFlow<GnssStatusSnapshot?>(null)
    val status: StateFlow<GnssStatusSnapshot?> = _status.asStateFlow()

    fun publish(snapshot: GnssStatusSnapshot) {
        _status.value = snapshot
    }

    fun clear() {
        _status.value = null
    }
}
