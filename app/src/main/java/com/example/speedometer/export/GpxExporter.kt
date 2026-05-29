package com.example.speedometer.export

import android.content.Context
import androidx.core.content.FileProvider
import com.example.speedometer.data.LocationPoint
import com.example.speedometer.data.Trip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    private val isoFormatter: SimpleDateFormat = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun export(context: Context, trip: Trip, points: List<LocationPoint>): android.net.Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = trip.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "${safeName}_${trip.id}.gpx")
        file.writeText(buildGpx(trip, points))
        return FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
    }

    private fun buildGpx(trip: Trip, points: List<LocationPoint>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine(
            "<gpx version=\"1.1\" creator=\"Speedometer\" " +
                "xmlns=\"http://www.topografix.com/GPX/1/1\">"
        )
        appendLine("  <metadata>")
        appendLine("    <name>${trip.name.xml()}</name>")
        appendLine("    <time>${iso(trip.startedAt)}</time>")
        appendLine("  </metadata>")
        appendLine("  <trk>")
        appendLine("    <name>${trip.name.xml()}</name>")
        appendLine("    <trkseg>")
        for (p in points) {
            appendLine(
                "      <trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">"
            )
            p.altitude?.let { appendLine("        <ele>$it</ele>") }
            appendLine("        <time>${iso(p.timestamp)}</time>")
            appendLine("        <extensions><speed>${p.speedMps}</speed></extensions>")
            appendLine("      </trkpt>")
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    private fun iso(epochMs: Long): String = isoFormatter.format(Date(epochMs))
    private fun String.xml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
