package com.example.speedometer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {
    @Insert
    suspend fun insert(point: LocationPoint): Long

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getForTrip(tripId: Long): List<LocationPoint>

    @Query("SELECT * FROM location_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeForTrip(tripId: Long): Flow<List<LocationPoint>>

    @Query("SELECT COUNT(*) FROM location_points WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Long): Int
}
