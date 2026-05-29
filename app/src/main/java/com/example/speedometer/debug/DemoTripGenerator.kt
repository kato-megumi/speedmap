package com.example.speedometer.debug

import com.example.speedometer.data.LocationPoint
import com.example.speedometer.data.TripMath
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates a synthetic sequence of LocationPoints along the great-circle path
 * between two coordinates. Used for testing the map / trip detail UI without
 * having to physically drive somewhere.
 */
object DemoTripGenerator {

    /**
     * @param startLat / startLon origin
     * @param endLat   / endLon   destination
     * @param sampleCount number of points to generate (including both endpoints)
     * @param avgSpeedMps cruising speed, used to derive timestamps and per-point speed
     * @param startMillis epoch ms at the first point; defaults to "now"
     * @param tripId placeholder tripId (the repository overwrites it on insert)
     */
    fun generate(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        sampleCount: Int = 200,
        avgSpeedMps: Double = 60.0, // ~216 km/h, train-ish
        startMillis: Long = System.currentTimeMillis(),
        tripId: Long = 0L
    ): List<LocationPoint> {
        require(sampleCount >= 2) { "need at least 2 samples" }
        val totalMeters = TripMath.haversineMeters(startLat, startLon, endLat, endLon)
        val totalDurMs = ((totalMeters / avgSpeedMps) * 1000.0).toLong().coerceAtLeast(1000L)
        val out = ArrayList<LocationPoint>(sampleCount)

        val lat1 = Math.toRadians(startLat)
        val lon1 = Math.toRadians(startLon)
        val lat2 = Math.toRadians(endLat)
        val lon2 = Math.toRadians(endLon)
        // Great-circle interpolation (spherical linear interpolation).
        val d = 2.0 * Math.asin(
            sqrt(
                sin((lat2 - lat1) / 2).let { it * it } +
                    cos(lat1) * cos(lat2) * sin((lon2 - lon1) / 2).let { it * it }
            )
        )

        for (i in 0 until sampleCount) {
            val f = i.toDouble() / (sampleCount - 1)
            val lat: Double; val lon: Double
            if (d == 0.0) {
                lat = startLat; lon = startLon
            } else {
                val a = sin((1 - f) * d) / sin(d)
                val b = sin(f * d) / sin(d)
                val x = a * cos(lat1) * cos(lon1) + b * cos(lat2) * cos(lon2)
                val y = a * cos(lat1) * sin(lon1) + b * cos(lat2) * sin(lon2)
                val z = a * sin(lat1) + b * sin(lat2)
                lat = Math.toDegrees(atan2(z, sqrt(x * x + y * y)))
                lon = Math.toDegrees(atan2(y, x))
            }
            // Speed profile: ease-in / ease-out using sin curve so max != avg.
            val speedFactor = sin(f * PI).coerceAtLeast(0.2)
            val speed = (avgSpeedMps * (0.6 + 0.8 * speedFactor)).toFloat()
            // Altitude profile: three overlapping sine waves give a mountain-
            // pass look (rolling hills with a big peak in the middle), so the
            // elevation colormap actually has structure to show.
            val altitude = (
                400.0 * sin(f * PI) +                   // big midpoint hump
                    180.0 * sin(f * PI * 5.0) +         // rolling hills
                    80.0 * sin(f * PI * 13.0) +         // small bumps
                    50.0                                 // sea-level offset
                ).coerceAtLeast(0.0)
            val bearing = 0f // fixed up below
            out += LocationPoint(
                tripId = tripId,
                timestamp = startMillis + (totalDurMs * f).toLong(),
                latitude = lat,
                longitude = lon,
                altitude = altitude,
                speedMps = speed,
                bearing = bearing,
                accuracy = 5f
            )
        }
        // Second pass: fix bearings using successor points.
        for (i in 0 until out.size - 1) {
            val a = out[i]; val b = out[i + 1]
            out[i] = a.copy(bearing = bearingDeg(a.latitude, a.longitude, b.latitude, b.longitude))
        }
        return out
    }

    private fun bearingDeg(lat1d: Double, lon1d: Double, lat2d: Double, lon2d: Double): Float {
        val lat1 = Math.toRadians(lat1d); val lat2 = Math.toRadians(lat2d)
        val dLon = Math.toRadians(lon2d - lon1d)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toFloat()
    }
}
