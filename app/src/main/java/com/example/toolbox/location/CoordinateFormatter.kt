package com.example.toolbox.location

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

fun formatDecimalDegrees(value: Double): String =
    String.format(Locale.US, "%.6f°", value)

fun formatShortCoordinates(latitude: Double, longitude: Double): String {
    val latSuffix = if (latitude < 0.0) 'S' else 'N'
    val lonSuffix = if (longitude < 0.0) 'W' else 'E'
    return String.format(
        Locale.US,
        "%.2f°%c, %.2f°%c",
        abs(latitude),
        latSuffix,
        abs(longitude),
        lonSuffix,
    )
}

fun formatLatitudeDms(value: Double): String = formatDms(value, 'N', 'S')

fun formatLongitudeDms(value: Double): String = formatDms(value, 'E', 'W')

private fun formatDms(value: Double, positive: Char, negative: Char): String {
    val totalSeconds = (abs(value) * 3_600.0 * 100.0).roundToInt() / 100.0
    var degrees = floor(totalSeconds / 3_600.0).toInt()
    var remainingSeconds = totalSeconds - degrees * 3_600.0
    var minutes = floor(remainingSeconds / 60.0).toInt()
    var seconds = remainingSeconds - minutes * 60.0

    if (seconds >= 60.0) {
        seconds = 0.0
        minutes += 1
    }
    if (minutes >= 60) {
        minutes = 0
        degrees += 1
    }

    return String.format(
        Locale.US,
        "%d° %02d′ %05.2f″ %c",
        degrees,
        minutes,
        seconds,
        if (value < 0.0) negative else positive,
    )
}
