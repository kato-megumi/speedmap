package com.example.speedometer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMathTest {

    // Reference distances computed externally (NOAA great-circle calculator).
    // 1 degree of latitude ≈ 111_195 m at the equator.
    @Test
    fun haversine_oneDegreeOfLatitude_isAbout111km() {
        val d = TripMath.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, d, 50.0) // within 50 m
    }

    @Test
    fun haversine_samePoint_isZero() {
        val d = TripMath.haversineMeters(35.681236, 139.767125, 35.681236, 139.767125)
        assertEquals(0.0, d, 1e-6)
    }

    @Test
    fun haversine_tokyoToOsaka_isAbout400km() {
        // Tokyo Station → Osaka Station, great-circle distance ≈ 400 km
        val d = TripMath.haversineMeters(35.6812, 139.7671, 34.7024, 135.4959)
        assertEquals(400_000.0, d, 10_000.0)
    }

    @Test
    fun haversine_isSymmetric() {
        val a = TripMath.haversineMeters(10.0, 20.0, 11.0, 21.0)
        val b = TripMath.haversineMeters(11.0, 21.0, 10.0, 20.0)
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun aggregates_firstPoint_setsCountAndMaxFromSpeed() {
        val trip = Trip(id = 1, name = "t", startedAt = 1_000L)
        val p1 = point(tripId = 1, t = 2_000L, lat = 0.0, lon = 0.0, speed = 5f)
        val updated = TripMath.updateAggregates(trip, previousPoint = null, newPoint = p1, pointCount = 1)
        assertEquals(0.0, updated.distanceMeters, 1e-6)
        assertEquals(5f, updated.maxSpeedMps, 1e-6f)
        assertEquals(5f, updated.avgSpeedMps, 1e-6f)
        assertEquals(1, updated.pointCount)
    }

    @Test
    fun aggregates_secondPoint_accumulatesDistanceAndComputesAvg() {
        val trip = Trip(
            id = 1, name = "t", startedAt = 0L,
            distanceMeters = 0.0, maxSpeedMps = 5f, avgSpeedMps = 5f, pointCount = 1
        )
        val prev = point(tripId = 1, t = 0L, lat = 0.0, lon = 0.0, speed = 5f)
        // ~111 km north over 1 hour → avg ≈ 30.9 m/s
        val next = point(tripId = 1, t = 3_600_000L, lat = 1.0, lon = 0.0, speed = 10f)
        val updated = TripMath.updateAggregates(trip, prev, next, pointCount = 2)
        assertEquals(111_195.0, updated.distanceMeters, 50.0)
        assertEquals(2, updated.pointCount)
        assertEquals(10f, updated.maxSpeedMps, 1e-6f)
        // avg ≈ distance / 3600 s
        assertTrue("avg should be ~30 m/s, was ${updated.avgSpeedMps}", updated.avgSpeedMps in 30.0f..31.5f)
    }

    @Test
    fun aggregates_maxSpeedNeverDecreases() {
        val trip = Trip(id = 1, name = "t", startedAt = 0L, maxSpeedMps = 25f, pointCount = 5)
        val prev = point(1, 1_000L, 0.0, 0.0, 25f)
        val next = point(1, 2_000L, 0.0001, 0.0, 10f)
        val updated = TripMath.updateAggregates(trip, prev, next, pointCount = 6)
        assertEquals(25f, updated.maxSpeedMps, 1e-6f)
    }

    private fun point(tripId: Long, t: Long, lat: Double, lon: Double, speed: Float) =
        LocationPoint(
            tripId = tripId, timestamp = t,
            latitude = lat, longitude = lon, altitude = 0.0,
            speedMps = speed, bearing = 0f, accuracy = 1f
        )
}
