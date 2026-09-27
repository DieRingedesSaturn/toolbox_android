package com.example.toolbox.astronomy

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.example.toolbox.location.formatShortCoordinates
import com.example.toolbox.ui.ToolboxStrings
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportThemePalette(
    val backgroundColor: Int,
    val cardBackgroundColor: Int,
    val cardInnerBackgroundColor: Int,
    val textPrimaryColor: Int,
    val textSecondaryColor: Int,
    val primaryColor: Int,
    val secondaryColor: Int,
    val outlineColor: Int,
    val errorColor: Int,
)

object AstronomyImageExporter {

    fun exportNightTimelineImage(
        context: Context,
        strings: ToolboxStrings,
        timeline: NightTimeline,
        palette: ExportThemePalette,
    ): Uri? {
        val width = 1080
        val height = 1720
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Fill Main Background
        canvas.drawColor(palette.backgroundColor)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val pad = 44f

        // 2. Top Header Card
        val headerRect = RectF(pad, pad, width - pad, pad + 140f)
        shapePaint.color = palette.cardBackgroundColor
        canvas.drawRoundRect(headerRect, 24f, 24f, shapePaint)

        textPaint.color = palette.primaryColor
        textPaint.textSize = 38f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${strings.appName} · ${strings.astronomyTitle}", pad + 32f, pad + 56f, textPaint)

        textPaint.color = palette.textSecondaryColor
        textPaint.textSize = 26f
        textPaint.isFakeBoldText = false
        val coordStr = formatShortCoordinates(timeline.observerLatitude, timeline.observerLongitude)
        val dateStr = dateFmt.format(Date(timeline.referenceDateMillis))
        val locText = "${strings.observerCoordinates}: $coordStr  ·  ${strings.dateLabel}: $dateStr"
        canvas.drawText(locText, pad + 32f, pad + 102f, textPaint)

        // 3. Moon Phase Hero Card (Matching App Screen)
        val moonCardTop = headerRect.bottom + 20f
        val moonCardRect = RectF(pad, moonCardTop, width - pad, moonCardTop + 146f)
        shapePaint.color = palette.cardBackgroundColor
        canvas.drawRoundRect(moonCardRect, 24f, 24f, shapePaint)

        // Moon emoji
        textPaint.textSize = 60f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(timeline.moonPhase.phase.emoji, pad + 65f, moonCardTop + 95f, textPaint)

        // Moon phase name & age tag
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = palette.textPrimaryColor
        textPaint.textSize = 32f
        textPaint.isFakeBoldText = true
        canvas.drawText(strings.moonPhaseName(timeline.moonPhase.phase), pad + 130f, moonCardTop + 55f, textPaint)

        val moonAgeStr = "%.1f".format(Locale.US, timeline.moonPhase.moonAgeDays)
        val moonAgeTag = "${strings.moonAge} $moonAgeStr ${strings.daysUnit}"
        shapePaint.color = palette.cardInnerBackgroundColor
        val tagRect = RectF(width - pad - 200f, moonCardTop + 24f, width - pad - 24f, moonCardTop + 68f)
        canvas.drawRoundRect(tagRect, 12f, 12f, shapePaint)
        textPaint.color = palette.textSecondaryColor
        textPaint.textSize = 22f
        textPaint.isFakeBoldText = false
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(moonAgeTag, tagRect.centerX(), tagRect.centerY() + 8f, textPaint)

        // Moon illumination progress track
        val barLeft = pad + 130f
        val barRight = width - pad - 24f
        val barY = moonCardTop + 92f
        val barHeight = 16f
        val trackRect = RectF(barLeft, barY, barRight, barY + barHeight)
        shapePaint.color = palette.cardInnerBackgroundColor
        canvas.drawRoundRect(trackRect, 8f, 8f, shapePaint)

        val illumFrac = (timeline.moonPhase.illuminationPercent / 100f).coerceIn(0f, 1f)
        if (illumFrac > 0f) {
            val fillRect = RectF(barLeft, barY, barLeft + (trackRect.width() * illumFrac), barY + barHeight)
            shapePaint.color = palette.primaryColor
            canvas.drawRoundRect(fillRect, 8f, 8f, shapePaint)
        }

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = palette.primaryColor
        textPaint.textSize = 22f
        textPaint.isFakeBoldText = true
        canvas.drawText("${strings.moonIllumination}: ${timeline.moonPhase.illuminationPercent}%", barLeft, barY + 40f, textPaint)

        // 4. Night Timeline Showcase Card (Gantt Chart with Pixel-Perfect Alignment)
        val chartTop = moonCardRect.bottom + 20f
        val chartHeight = 620f
        val chartRect = RectF(pad, chartTop, width - pad, chartTop + chartHeight)
        shapePaint.color = palette.cardBackgroundColor
        canvas.drawRoundRect(chartRect, 24f, 24f, shapePaint)

        // Twilight 3 Chips Header
        val chipTop = chartTop + 20f
        val chipHeight = 56f
        val chipW = (chartRect.width() - 48f - 32f) / 3f

        val sunsetTime = timeFmt.format(Date(timeline.sunsetMillis))
        val darkTime = "${timeFmt.format(Date(timeline.duskAstronomicalMillis))} - ${timeFmt.format(Date(timeline.dawnAstronomicalMillis))}"
        val sunriseTime = timeFmt.format(Date(timeline.sunriseMillis))

        drawTwilightChip(canvas, chartRect.left + 24f, chipTop, chipW, chipHeight, "🌅", strings.sunsetLabel, sunsetTime, 0xFFFFB74D.toInt(), palette)
        drawTwilightChip(canvas, chartRect.left + 24f + chipW + 16f, chipTop, chipW, chipHeight, "🌌", strings.darkNightLabel, darkTime, palette.primaryColor, palette)
        drawTwilightChip(canvas, chartRect.left + 24f + (chipW + 16f) * 2f, chipTop, chipW, chipHeight, "🌄", strings.sunriseLabel, sunriseTime, 0xFFFFB74D.toInt(), palette)

        val windowStart = timeline.sunsetMillis - (30 * 60000L)
        val windowEnd = timeline.sunriseMillis + (30 * 60000L)
        val totalDuration = (windowEnd - windowStart).coerceAtLeast(1L)

        val labelAreaWidth = 140f
        val ganttLeft = chartRect.left + labelAreaWidth + 24f
        val ganttRight = chartRect.right - 24f
        val ganttWidth = ganttRight - ganttLeft
        val ganttTop = chipTop + chipHeight + 42f
        val ganttBottom = chartRect.bottom - 44f
        val ganttHeight = ganttBottom - ganttTop

        // Time Ruler Labels on Top
        val numTicks = 5
        textPaint.color = palette.textSecondaryColor
        textPaint.textSize = 22f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = false
        for (i in 0..numTicks) {
            val frac = i.toFloat() / numTicks
            val tickX = ganttLeft + (ganttWidth * frac)
            val tickTime = windowStart + (totalDuration * frac).toLong()
            canvas.drawText(timeFmt.format(Date(tickTime)), tickX, ganttTop - 12f, textPaint)
        }

        // Draw Row-by-Row with Fixed Height & Exact Vertical Centering
        val rowCount = timeline.bodies.size
        val rowHeight = ganttHeight / rowCount.coerceAtLeast(1)

        val duskEndFrac = ((timeline.duskAstronomicalMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
        val dawnStartFrac = ((timeline.dawnAstronomicalMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
        val nowMillis = System.currentTimeMillis()

        timeline.bodies.forEachIndexed { index, body ->
            val rowY = ganttTop + (index * rowHeight)
            val rowCenterY = rowY + (rowHeight / 2f)

            // 1. Left Icon Circle + Name (Vertically Centered at rowCenterY)
            val iconX = chartRect.left + 40f
            val iconRadius = 18f
            val bodyColor = (body.body.colorRgb.toInt() and 0x00FFFFFF) or 0xFF000000.toInt()

            shapePaint.color = (bodyColor and 0x00FFFFFF) or 0x40000000
            canvas.drawCircle(iconX, rowCenterY, iconRadius, shapePaint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 20f
            textPaint.color = palette.textPrimaryColor
            canvas.drawText(body.body.symbol, iconX, rowCenterY + 7f, textPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 24f
            textPaint.isFakeBoldText = true
            canvas.drawText(strings.bodyName(body), iconX + 26f, rowCenterY + 8f, textPaint)

            // 2. Right Sky Track for this row (Clean rectangular track)
            val trackRect = RectF(ganttLeft, rowY + 4f, ganttRight, rowY + rowHeight - 4f)
            shapePaint.color = palette.cardInnerBackgroundColor
            canvas.drawRect(trackRect, shapePaint)

            // Dusk & Dawn Twilight Shading
            shapePaint.color = (palette.primaryColor and 0x00FFFFFF) or 0x22000000
            val duskRect = RectF(ganttLeft, trackRect.top, ganttLeft + (ganttWidth * duskEndFrac), trackRect.bottom)
            canvas.drawRect(duskRect, shapePaint)
            val dawnRect = RectF(ganttLeft + (ganttWidth * dawnStartFrac), trackRect.top, ganttRight, trackRect.bottom)
            canvas.drawRect(dawnRect, shapePaint)

            // Vertical grid line dots
            for (i in 0..numTicks) {
                val frac = i.toFloat() / numTicks
                val tickX = ganttLeft + (ganttWidth * frac)
                shapePaint.color = palette.outlineColor
                shapePaint.strokeWidth = 1f
                canvas.drawLine(tickX, trackRect.top, tickX, trackRect.bottom, shapePaint)
            }

            // 3. Rectangular Visibility Bar (Strictly covering full track height)
            shapePaint.color = bodyColor

            body.visibleIntervals.forEach { interval ->
                val startFrac = ((interval.startMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
                val endFrac = ((interval.endMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
                val barL = ganttLeft + (ganttWidth * startFrac)
                val barW = (ganttWidth * (endFrac - startFrac)).coerceAtLeast(10f)

                val barRect = RectF(barL, trackRect.top, barL + barW, trackRect.bottom)
                canvas.drawRect(barRect, shapePaint)

                // Large, readable Max Altitude Text inside bar
                if (barW > 70f && body.maxAltitudeDegrees > 0) {
                    textPaint.color = 0xFF000000.toInt()
                    textPaint.textSize = 24f
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.isFakeBoldText = true
                    canvas.drawText("%.0f°".format(Locale.US, body.maxAltitudeDegrees), barL + (barW / 2f), rowCenterY + 8f, textPaint)
                }
            }

            // 4. Current Time Needle in Row
            if (nowMillis in windowStart..windowEnd) {
                val nowFrac = ((nowMillis - windowStart).toFloat() / totalDuration).coerceIn(0f, 1f)
                val nowX = ganttLeft + (ganttWidth * nowFrac)
                shapePaint.color = palette.errorColor
                shapePaint.strokeWidth = 3f
                canvas.drawLine(nowX, trackRect.top, nowX, trackRect.bottom, shapePaint)
            }
        }

        // Legend at bottom of Gantt card
        val legendY = chartRect.bottom - 16f
        drawLegend(canvas, chartRect.left + 24f, legendY, 0xFF4CAF50.toInt(), strings.legendVisiblePeriod, palette)
        drawLegend(canvas, chartRect.left + 150f, legendY, palette.cardInnerBackgroundColor, strings.darkNightLabel, palette)
        drawLegend(canvas, chartRect.left + 276f, legendY, palette.errorColor, strings.legendCurrentTime, palette)

        // 5. Celestial Bodies Summary Cards (2x2 Structure matching App UI)
        val detailsTop = chartRect.bottom + 20f
        val detailsHeight = 630f
        val detailsRect = RectF(pad, detailsTop, width - pad, detailsTop + detailsHeight)
        shapePaint.color = palette.cardBackgroundColor
        canvas.drawRoundRect(detailsRect, 24f, 24f, shapePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = palette.primaryColor
        textPaint.textSize = 30f
        textPaint.isFakeBoldText = true
        canvas.drawText(strings.celestialBodiesTitle, pad + 28f, detailsTop + 44f, textPaint)

        var cardY = detailsTop + 70f
        val itemHeight = 100f
        val displayBodies = timeline.bodies.sortedByDescending { it.isVisibleTonight }.take(5)

        displayBodies.forEach { body ->
            val bodyColor = (body.body.colorRgb.toInt() and 0x00FFFFFF) or 0xFF000000.toInt()
            val itemBox = RectF(pad + 20f, cardY, width - pad - 20f, cardY + itemHeight - 8f)
            shapePaint.color = palette.cardInnerBackgroundColor
            canvas.drawRoundRect(itemBox, 14f, 14f, shapePaint)

            // Avatar + Name
            val avX = itemBox.left + 32f
            val avY = itemBox.centerY()
            shapePaint.color = (bodyColor and 0x00FFFFFF) or 0x40000000
            canvas.drawCircle(avX, avY, 18f, shapePaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 20f
            textPaint.color = palette.textPrimaryColor
            canvas.drawText(body.body.symbol, avX, avY + 7f, textPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 26f
            textPaint.isFakeBoldText = true
            canvas.drawText(strings.bodyName(body), avX + 28f, itemBox.top + 34f, textPaint)

            // Status tag
            val maxAltStr = "%.0f°".format(Locale.US, body.maxAltitudeDegrees)
            val statusText = if (body.isVisibleTonight) strings.visibleTonight else strings.notVisibleTonight
            val statusTag = "$statusText (${strings.maxAltitude} $maxAltStr)"
            textPaint.color = if (body.isVisibleTonight) 0xFF4CAF50.toInt() else palette.textSecondaryColor
            textPaint.textSize = 20f
            textPaint.isFakeBoldText = true
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(statusTag, itemBox.right - 20f, itemBox.top + 34f, textPaint)

            // Metrics row
            val riseStr = body.riseTimeMillis?.let { timeFmt.format(Date(it)) } ?: "--:--"
            val setStr = body.setTimeMillis?.let { timeFmt.format(Date(it)) } ?: "--:--"
            val transitStr = body.transitTimeMillis?.let { timeFmt.format(Date(it)) } ?: "--:--"
            val magStr = body.magnitude?.let { "%.1f mag".format(Locale.US, it) } ?: "--"

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 21f
            textPaint.color = palette.textSecondaryColor
            textPaint.isFakeBoldText = false
            val metricsText = "${strings.riseSetLabel}: $riseStr → $setStr   ·   ${strings.transitTime}: $transitStr   ·   ${strings.visualMagnitude}: $magStr"
            canvas.drawText(metricsText, avX + 28f, itemBox.top + 68f, textPaint)

            cardY += itemHeight + 4f
        }

        // Footer Brand
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = palette.textSecondaryColor
        textPaint.textSize = 20f
        textPaint.isFakeBoldText = false
        canvas.drawText(strings.generatedByToolbox, width / 2f, height - 20f, textPaint)

        return saveBitmap(context, bitmap)
    }

    private fun drawTwilightChip(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        symbol: String,
        label: String,
        time: String,
        timeColor: Int,
        palette: ExportThemePalette,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = palette.cardInnerBackgroundColor
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 12f, 12f, paint)

        paint.textSize = 22f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(symbol, x + 12f, y + 36f, paint)

        paint.color = palette.textSecondaryColor
        paint.textSize = 17f
        paint.isFakeBoldText = false
        canvas.drawText(label, x + 44f, y + 23f, paint)

        paint.color = timeColor
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText(time, x + 44f, y + 47f, paint)
    }

    private fun drawLegend(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int,
        label: String,
        palette: ExportThemePalette,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawCircle(x, y - 6f, 6f, paint)

        paint.color = palette.textSecondaryColor
        paint.textSize = 18f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, x + 12f, y, paint)
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap): Uri? {
        return runCatching {
            val fileName = "Toolbox_Astronomy_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Toolbox")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                uri
            } else {
                val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportsDir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }.getOrNull()
    }
}
