package com.example.speedometer.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure functions used by [TripRepository]; kept here so they can be unit-tested on the JVM. */
object TripMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance between two (lat, lon) pairs, in meters. */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)
        val a = sinDLat * sinDLat +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sinDLon * sinDLon
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /**
     * Recompute trip aggregates after appending [newPoint].
     *
     * @param trip current trip row (before update)
     * @param previousPoint immediately preceding stored point, or `null` if [newPoint] is the first
     * @param newPoint the just-appended point
     * @param pointCount total stored points including [newPoint]
     */
    fun updateAggregates(
        trip: Trip,
        previousPoint: LocationPoint?,
        newPoint: LocationPoint,
        pointCount: Int
    ): Trip {
        val segment = previousPoint?.let {
            haversineMeters(it.latitude, it.longitude, newPoint.latitude, newPoint.longitude)
        } ?: 0.0
        val newDistance = trip.distanceMeters + segment
        val durationSec = ((newPoint.timestamp - trip.startedAt) / 1000.0).coerceAtLeast(1.0)
        val avg = if (previousPoint == null) newPoint.speedMps
                  else (newDistance / durationSec).toFloat()
        return trip.copy(
            distanceMeters = newDistance,
            maxSpeedMps = maxOf(trip.maxSpeedMps, newPoint.speedMps),
            avgSpeedMps = avg,
            pointCount = pointCount
        )
    }
}
