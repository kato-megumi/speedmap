package com.example.speedometer.util

import java.util.concurrent.TimeUnit

object Formatters {
    fun distance(meters: Double): String =
        if (meters >= 1000) String.format("%.2f km", meters / 1000.0)
        else String.format("%.0f m", meters)

    fun duration(millis: Long): String {
        val s = TimeUnit.MILLISECONDS.toSeconds(millis)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec)
        else String.format("%02d:%02d", m, sec)
    }
}
