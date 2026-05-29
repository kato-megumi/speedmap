package com.example.speedometer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Float = 0f,
    val avgSpeedMps: Float = 0f,
    val pointCount: Int = 0
) {
    val isActive: Boolean get() = endedAt == null
    val durationMillis: Long
        get() = (endedAt ?: System.currentTimeMillis()) - startedAt
}
