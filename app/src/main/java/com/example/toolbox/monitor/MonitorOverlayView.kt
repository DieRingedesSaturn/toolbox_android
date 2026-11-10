package com.example.toolbox.monitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.roundToInt

class MonitorOverlayView @JvmOverloads constructor(
    context: Context,
    private val onStop: () -> Unit = {},
    private val onMove: (Float, Float) -> Unit = { _, _ -> },
    private val onDragEnd: () -> Unit = {},
) : View(context) {
    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // A light translucent surface keeps the compact overlay readable over both
        // light and dark apps without trying to sample pixels behind the window.
        color = 0xBFFFFFFF.toInt()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3D303038
        strokeWidth = dp(1f)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dp(1.5f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val accentFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val sparkFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF202124.toInt()
        textSize = dp(13f)
    }
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5F6368.toInt()
        textSize = dp(10f)
    }
    private val barBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33888888
    }
    private val closeBounds = RectF()
    private val barRect = RectF()
    private val history = mutableMapOf<MonitorMetric, MutableList<Float>>()
    private var metrics: List<MonitorMetric> = MonitorMetric.entries.toList()
    private var cpuDisplayMode = MonitorCpuDisplayMode.TOPOLOGY_MATRIX
    private var latestSample: MonitorSample? = null
    private var chinese = false
    private var accentColor = 0xFF8AB4F8.toInt()
    private var fixedPosition = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    private var closing = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        isClickable = true
        setWillNotDraw(false)
    }

    fun updateConfig(
        metrics: Set<MonitorMetric>,
        cpuDisplayMode: MonitorCpuDisplayMode,
        chinese: Boolean,
        accentColor: Int,
        fixedPosition: Boolean,
        colors: MonitorOverlayColors = MonitorOverlayThemeHelper.resolveColors(MonitorOverlayTheme.DARK),
    ) {
        this.metrics = metrics.sortedBy { it.ordinal }.ifEmpty { MonitorMetric.entries.toList() }
        this.cpuDisplayMode = cpuDisplayMode
        this.chinese = chinese
        this.accentColor = accentColor
        this.fixedPosition = fixedPosition
        backgroundPaint.color = colors.backgroundColor
        textPaint.color = colors.primaryTextColor
        secondaryTextPaint.color = colors.secondaryTextColor
        gridPaint.color = colors.gridColor
        accentPaint.color = accentColor
        accentFillPaint.color = accentColor
        sparkFillPaint.color = (accentColor and 0x00FFFFFF) or 0x1F000000
        history.keys.retainAll(this.metrics.toSet())
        invalidate()
    }

    fun updateSample(sample: MonitorSample) {
        latestSample = sample
        metrics.forEach { metric ->
            val value = if (metric == MonitorMetric.CPU && cpuDisplayMode == MonitorCpuDisplayMode.WEIGHTED_USAGE) {
                sample.weightedCpuPercent()
            } else {
                sample.chartValue(metric)
            }
            if (value != null) {
                val values = history.getOrPut(metric) { mutableListOf() }
                values += value.coerceIn(0f, 100f)
                if (values.size > 60) values.removeAt(0)
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = dp(8f)
        val headerHeight = dp(16f)
        canvas.drawRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            dp(14f),
            dp(14f),
            backgroundPaint,
        )
        textPaint.textSize = dp(18f)
        canvas.drawText("×", width - padding - dp(12f), padding + dp(13f), textPaint)
        closeBounds.set(width - dp(32f), 0f, width.toFloat(), headerHeight + padding)

        var top = padding + headerHeight
        metrics.forEach { metric ->
            drawMetric(canvas, metric, top, padding)
            top += metricRowHeight(metric)
        }
    }

    private fun drawMetric(canvas: Canvas, metric: MonitorMetric, top: Float, padding: Float) {
        val sample = latestSample
        if (metric == MonitorMetric.CPU) {
            when (cpuDisplayMode) {
                MonitorCpuDisplayMode.TOPOLOGY_MATRIX -> {
                    drawCoreTopologyMatrix(canvas, sample, top, padding)
                    return
                }
                MonitorCpuDisplayMode.CORE_FREQUENCIES -> {
                    drawCoreFrequencies(canvas, sample, top, padding)
                    return
                }
                MonitorCpuDisplayMode.WEIGHTED_USAGE -> {
                    // Fall through to single line chart
                }
            }
        }
        val label = when (metric) {
            MonitorMetric.CPU -> "CPU"
            MonitorMetric.GPU -> "GPU"
            MonitorMetric.MEMORY -> if (chinese) "内存" else "Mem"
            MonitorMetric.BATTERY -> if (chinese) "电量" else "Batt"
            MonitorMetric.FPS -> "FPS"
        }
        val display = displayValue(metric, sample)
        textPaint.textSize = dp(11f)
        canvas.drawText(label, padding, top + dp(12f), textPaint)
        secondaryTextPaint.textSize = dp(10f)
        val displayWidth = secondaryTextPaint.measureText(display)
        canvas.drawText(display, width - padding - displayWidth, top + dp(12f), secondaryTextPaint)

        val chartTop = top + dp(21f)
        val chartBottom = top + metricRowHeight(metric) - dp(3f)
        val chartRight = width - padding
        canvas.drawLine(padding, chartBottom, chartRight, chartBottom, gridPaint)

        val values = history[metric].orEmpty()
        if (values.size >= 2) {
            val line = Path()
            val fill = Path()
            val lastIndex = values.lastIndex.coerceAtLeast(1)
            values.forEachIndexed { valueIndex, value ->
                val x = padding + (chartRight - padding) * valueIndex / lastIndex
                val y = chartBottom - (chartBottom - chartTop) * value / 100f
                if (valueIndex == 0) {
                    line.moveTo(x, y)
                    fill.moveTo(x, chartBottom)
                    fill.lineTo(x, y)
                } else {
                    line.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(chartRight, chartBottom)
            fill.close()
            canvas.drawPath(fill, sparkFillPaint)
            canvas.drawPath(line, accentPaint)
        }
    }

    private fun drawCoreTopologyMatrix(
        canvas: Canvas,
        sample: MonitorSample?,
        top: Float,
        padding: Float,
    ) {
        textPaint.textSize = dp(11f)
        canvas.drawText("CPU", padding, top + dp(12f), textPaint)

        val display = displayValue(MonitorMetric.CPU, sample)
        secondaryTextPaint.textSize = dp(10f)
        val displayWidth = secondaryTextPaint.measureText(display)
        canvas.drawText(display, width - padding - displayWidth, top + dp(12f), secondaryTextPaint)

        val cores = sample?.cpuCores.orEmpty()
        if (cores.isEmpty()) {
            return
        }

        val groups = cores.groupBy { it.maximumFrequencyMhz ?: 0 }.toSortedMap().values.toList()
        val barTop = top + dp(18f)
        val barBottom = top + metricRowHeight(MonitorMetric.CPU) - dp(4f)
        val barHeight = barBottom - barTop
        val barGap = dp(1.5f)
        val clusterGap = dp(5f)
        val innerGaps = (cores.size - 1).coerceAtLeast(0)
        val extraGaps = (groups.size - 1).coerceAtLeast(0)
        val totalAvailable = (width - padding * 2f) - barGap * innerGaps - clusterGap * extraGaps
        val barWidth = if (cores.isEmpty()) 0f else totalAvailable / cores.size
        val radius = dp(2f)

        var curX = padding
        groups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) curX += clusterGap
            group.forEach { core ->
                val left = curX
                val right = left + barWidth
                curX = right + barGap

                barRect.set(left, barTop, right, barBottom)
                canvas.drawRoundRect(barRect, radius, radius, barBackgroundPaint)

                val minMhz = core.minimumFrequencyMhz ?: 0
                val maxMhz = core.maximumFrequencyMhz ?: 0
                val curMhz = core.currentFrequencyMhz
                val fraction = if (curMhz == null || curMhz <= 0 || maxMhz <= 0) {
                    0f
                } else if (maxMhz > minMhz) {
                    ((curMhz - minMhz).toFloat() / (maxMhz - minMhz).toFloat()).coerceIn(0f, 1f)
                } else {
                    (curMhz.toFloat() / maxMhz.toFloat()).coerceIn(0f, 1f)
                }
                if (fraction > 0.008f) {
                    val fillTop = barBottom - barHeight * fraction
                    barRect.set(left, fillTop, right, barBottom)
                    canvas.drawRoundRect(barRect, radius, radius, accentFillPaint)
                }
            }
        }
    }

    private fun drawCoreFrequencies(
        canvas: Canvas,
        sample: MonitorSample?,
        top: Float,
        padding: Float,
    ) {
        textPaint.textSize = dp(11f)
        canvas.drawText("CPU", padding, top + dp(12f), textPaint)
        val cores = sample?.cpuCores.orEmpty()
        if (cores.isEmpty()) {
            val waiting = waitingLabel()
            secondaryTextPaint.textSize = dp(10f)
            canvas.drawText(
                waiting,
                width - padding - secondaryTextPaint.measureText(waiting),
                top + dp(12f),
                secondaryTextPaint,
            )
            return
        }

        val columns = 4
        val columnWidth = (width - padding * 2f) / columns
        cores.chunked(columns).forEachIndexed { rowIndex, row ->
            val baseline = top + dp(28f + rowIndex * 14f)
            row.forEachIndexed { column, core ->
                val frequency = core.currentFrequencyMhz?.toString() ?: "—"
                canvas.drawText(
                    "C${core.index} $frequency",
                    padding + column * columnWidth,
                    baseline,
                    secondaryTextPaint,
                )
            }
        }
    }

    private fun displayValue(metric: MonitorMetric, sample: MonitorSample?): String {
        if (sample == null) return waitingLabel()
        return when (metric) {
            MonitorMetric.CPU -> sample.weightedCpuUsagePercent()?.let {
                "${it.roundToInt()}%"
            } ?: sample.weightedCpuFrequencyPercent()?.let {
                "≈${it.roundToInt()}%"
            } ?: waitingLabel()

            MonitorMetric.GPU -> sample.gpuUsagePercent?.let { "${it.roundToInt()}%" }
                ?: sample.frequencyLoadPercent(MonitorMetric.GPU)?.let {
                    "≈${it.roundToInt()}%"
                }
                ?: sample.gpuFrequencyMhz?.let { current ->
                    val maximum = sample.gpuMaxFrequencyMhz?.let { " / $it" }.orEmpty()
                    "$current MHz$maximum"
                }
                ?: unavailableLabel()

            MonitorMetric.MEMORY -> sample.memoryUsagePercent?.let { "${it.roundToInt()}%" } ?: unavailableLabel()
            MonitorMetric.BATTERY -> sample.batteryLevelPercent?.let {
                val power = sample.batteryPowerMw?.let { value -> " · ${value.roundToInt()} mW" }.orEmpty()
                "${it.roundToInt()}%$power"
            } ?: unavailableLabel()
            MonitorMetric.FPS -> sample.displayRefreshRateHz?.let {
                "≈${it.roundToInt()} Hz"
            } ?: unavailableLabel()
        }
    }

    private fun waitingLabel(): String = if (chinese) "等待数据" else "Waiting for data"

    private fun unavailableLabel(): String = if (chinese) "不可用" else "N/A"

    private fun metricRowHeight(metric: MonitorMetric): Float = when {
        metric == MonitorMetric.CPU && cpuDisplayMode == MonitorCpuDisplayMode.TOPOLOGY_MATRIX -> dp(52f)
        metric == MonitorMetric.CPU && cpuDisplayMode == MonitorCpuDisplayMode.CORE_FREQUENCIES -> dp(80f)
        else -> dp(56f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false
                closing = closeBounds.contains(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastRawX
                val dy = event.rawY - lastRawY
                if (abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop) {
                    dragging = true
                }
                if (dragging && !closing && !fixedPosition) onMove(dx, dy)
                lastRawX = event.rawX
                lastRawY = event.rawY
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = dragging
                if (wasDragging) onDragEnd()
                dragging = false
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    closing &&
                    !wasDragging &&
                    closeBounds.contains(event.x, event.y)
                ) {
                    performClick()
                }
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onStop()
        return true
    }

    private fun dp(value: Float): Float = value * density
}
