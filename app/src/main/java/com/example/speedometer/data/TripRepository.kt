package com.example.speedometer.data

import kotlinx.coroutines.flow.Flow

class TripRepository(
    private val tripDao: TripDao,
    private val pointDao: LocationPointDao
) {
    fun observeTrips(): Flow<List<Trip>> = tripDao.observeAll()
    fun observeTrip(id: Long): Flow<Trip?> = tripDao.observeById(id)
    fun observePoints(tripId: Long): Flow<List<LocationPoint>> = pointDao.observeForTrip(tripId)

    suspend fun getTrip(id: Long): Trip? = tripDao.getById(id)
    suspend fun getPoints(tripId: Long): List<LocationPoint> = pointDao.getForTrip(tripId)
    suspend fun getActiveTrip(): Trip? = tripDao.getActiveTrip()

    suspend fun startTrip(name: String): Long {
        val now = System.currentTimeMillis()
        return tripDao.insert(Trip(name = name, startedAt = now))
    }

    suspend fun appendPoint(point: LocationPoint) {
        pointDao.insert(point)
        val trip = tripDao.getById(point.tripId) ?: return
        val points = pointDao.getForTrip(point.tripId)
        val prev = if (points.size >= 2) points[points.size - 2] else null
        tripDao.update(TripMath.updateAggregates(trip, prev, point, points.size))
    }

    suspend fun stopTrip(tripId: Long) {
        val trip = tripDao.getById(tripId) ?: return
        if (trip.endedAt != null) return
        tripDao.update(trip.copy(endedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTrip(tripId: Long) = tripDao.delete(tripId)

    suspend fun renameTrip(tripId: Long, name: String) = tripDao.rename(tripId, name)

    /**
     * Insert a synthetic completed trip given a precomputed list of points.
     * Aggregates (distance / max / avg speed, start/end times) are derived from the points.
     */
    suspend fun insertSyntheticTrip(name: String, points: List<LocationPoint>): Long {
        require(points.isNotEmpty()) { "synthetic trip needs at least one point" }
        val startedAt = points.first().timestamp
        val endedAt = points.last().timestamp
        var distance = 0.0
        var maxSpeed = 0f
        for (i in points.indices) {
            if (i > 0) {
                val a = points[i - 1]; val b = points[i]
                distance += TripMath.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            }
            if (points[i].speedMps > maxSpeed) maxSpeed = points[i].speedMps
        }
        val durSec = ((endedAt - startedAt) / 1000.0).coerceAtLeast(1.0)
        val avg = (distance / durSec).toFloat()
        val tripId = tripDao.insert(
            Trip(
                name = name,
                startedAt = startedAt,
                endedAt = endedAt,
                distanceMeters = distance,
                maxSpeedMps = maxSpeed,
                avgSpeedMps = avg,
                pointCount = points.size
            )
        )
        for (p in points) pointDao.insert(p.copy(tripId = tripId))
        return tripId
    }
}
