package com.example.toolbox.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.toolbox.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MonitorOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var windowManager: WindowManager
    private lateinit var reader: MonitorReader
    private var overlayView: MonitorOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var monitorJob: Job? = null
    private var selectedMetrics: Set<MonitorMetric> = MonitorMetric.entries.toSet()
    private var cpuDisplayMode = MonitorCpuDisplayMode.CORE_FREQUENCIES
    private var fixedPosition = false
    private var chinese = false
    private var accentColor = 0xFF8AB4F8.toInt()
    private var overlayTheme = MonitorOverlayTheme.DARK
    private var overlayCustomColor = MonitorOverlayThemeHelper.DEFAULT_CUSTOM_COLOR_RGB
    private var overlayOpacity = MonitorOverlayThemeHelper.DEFAULT_OPACITY

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        reader = MonitorReader(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        selectedMetrics = intent?.getStringArrayListExtra(EXTRA_METRICS)
            ?.mapNotNull { runCatching { MonitorMetric.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.takeUnless { it.isNullOrEmpty() }
            ?: MonitorMetric.entries.toSet()
        cpuDisplayMode = intent?.getStringExtra(EXTRA_CPU_DISPLAY_MODE)
            ?.let { runCatching { MonitorCpuDisplayMode.valueOf(it) }.getOrNull() }
            ?: MonitorCpuDisplayMode.CORE_FREQUENCIES
        fixedPosition = intent?.getBooleanExtra(EXTRA_FIXED_POSITION, false) ?: false
        chinese = intent?.getBooleanExtra(EXTRA_CHINESE, false) ?: false
        accentColor = intent?.getIntExtra(EXTRA_ACCENT_COLOR, accentColor) ?: accentColor
        overlayTheme = intent?.getStringExtra(EXTRA_OVERLAY_THEME)
            ?.let { runCatching { MonitorOverlayTheme.valueOf(it) }.getOrNull() }
            ?: MonitorOverlayTheme.DARK
        overlayCustomColor = intent?.getIntExtra(
            EXTRA_OVERLAY_CUSTOM_COLOR,
            MonitorOverlayThemeHelper.DEFAULT_CUSTOM_COLOR_RGB,
        ) ?: MonitorOverlayThemeHelper.DEFAULT_CUSTOM_COLOR_RGB
        overlayOpacity = intent?.getFloatExtra(
            EXTRA_OVERLAY_OPACITY,
            MonitorOverlayThemeHelper.DEFAULT_OPACITY,
        ) ?: MonitorOverlayThemeHelper.DEFAULT_OPACITY

        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showOrUpdateOverlay()
        if (monitorJob == null) startSampling()
        running = true
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showOrUpdateOverlay() {
        val colors = MonitorOverlayThemeHelper.resolveColors(
            theme = overlayTheme,
            customRgb = overlayCustomColor,
            opacity = overlayOpacity,
        )
        val existingView = overlayView
        if (existingView == null) {
            val positionPreferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            val view = MonitorOverlayView(
                context = this,
                onStop = ::stopSelf,
                onMove = ::moveOverlay,
            )
            val params = WindowManager.LayoutParams(
                dp(270f),
                overlayHeight(selectedMetrics.size, cpuDisplayMode),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                overlayFlags(),
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                alpha = overlayWindowAlpha()
                val displayWidthPx = resources.displayMetrics.widthPixels
                val displayHeightPx = resources.displayMetrics.heightPixels
                x = positionPreferences.getInt(POSITION_X_KEY, dp(12f))
                    .coerceIn(0, (displayWidthPx - width).coerceAtLeast(0))
                y = positionPreferences.getInt(POSITION_Y_KEY, dp(100f))
                    .coerceIn(0, (displayHeightPx - height).coerceAtLeast(0))
            }
            view.updateConfig(selectedMetrics, cpuDisplayMode, chinese, accentColor, fixedPosition, colors)
            windowManager.addView(view, params)
            overlayView = view
            layoutParams = params
        } else {
            existingView.updateConfig(selectedMetrics, cpuDisplayMode, chinese, accentColor, fixedPosition, colors)
            layoutParams?.let { params ->
                params.height = overlayHeight(selectedMetrics.size, cpuDisplayMode)
                params.flags = overlayFlags()
                params.alpha = overlayWindowAlpha()
                windowManager.updateViewLayout(existingView, params)
            }
        }
    }

    private fun startSampling() {
        monitorJob = serviceScope.launch {
            while (isActive) {
                val sample = withContext(Dispatchers.IO) { reader.read() }
                withContext(Dispatchers.Main) { overlayView?.updateSample(sample) }
                delay(SAMPLE_INTERVAL_MILLIS)
            }
        }
    }

    private fun moveOverlay(dx: Float, dy: Float) {
        if (fixedPosition) return
        val view = overlayView ?: return
        val params = layoutParams ?: return
        params.x = (params.x + dx.roundToInt())
            .coerceIn(0, (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = (params.y + dy.roundToInt())
            .coerceIn(0, (resources.displayMetrics.heightPixels - params.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit {
            putInt(POSITION_X_KEY, params.x)
            putInt(POSITION_Y_KEY, params.y)
        }
    }

    private fun overlayHeight(metricCount: Int, cpuDisplayMode: MonitorCpuDisplayMode): Int {
        val extra = if (MonitorMetric.CPU in selectedMetrics) {
            when (cpuDisplayMode) {
                MonitorCpuDisplayMode.CORE_FREQUENCIES -> 24f
                MonitorCpuDisplayMode.TOPOLOGY_MATRIX -> 0f
                MonitorCpuDisplayMode.WEIGHTED_USAGE -> 0f
            }
        } else {
            0f
        }
        return dp(40f + metricCount * 56f + extra)
    }

    private fun overlayFlags(): Int {
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return if (fixedPosition) {
            // A locked overlay must not consume the touch gesture underneath it.
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            baseFlags
        }
    }

    private fun overlayWindowAlpha(): Float = if (fixedPosition) {
        // Keep the not-touchable overlay below Android's obscuring-opacity limit
        // so touches can pass through it on recent Android versions.
        0.79f
    } else {
        1f
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, MonitorOverlayService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            100,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(if (chinese) "Toolbox 实时监控" else "Toolbox monitor")
            .setContentText(if (chinese) "悬浮窗监控正在运行" else "Floating monitor is running")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    if (chinese) "停止" else "Stop",
                    stopPendingIntent,
                ).build(),
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Toolbox monitor",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        overlayView?.let { view -> runCatching { windowManager.removeView(view) } }
        overlayView = null
        layoutParams = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "toolbox_monitor"
        private const val NOTIFICATION_ID = 701
        private const val SAMPLE_INTERVAL_MILLIS = 1000L
        private const val ACTION_START = "com.example.toolbox.monitor.START"
        private const val ACTION_STOP = "com.example.toolbox.monitor.STOP"
        private const val EXTRA_METRICS = "metrics"
        private const val EXTRA_CPU_DISPLAY_MODE = "cpu_display_mode"
        private const val EXTRA_FIXED_POSITION = "fixed_position"
        private const val EXTRA_CHINESE = "chinese"
        private const val EXTRA_ACCENT_COLOR = "accent_color"
        private const val EXTRA_OVERLAY_THEME = "overlay_theme"
        private const val EXTRA_OVERLAY_CUSTOM_COLOR = "overlay_custom_color"
        private const val EXTRA_OVERLAY_OPACITY = "overlay_opacity"
        private const val PREFERENCES_NAME = "toolbox_preferences"
        private const val POSITION_X_KEY = "monitor_overlay_position_x"
        private const val POSITION_Y_KEY = "monitor_overlay_position_y"

        @Volatile
        var running: Boolean = false
            private set

        fun start(
            context: Context,
            metrics: Set<MonitorMetric>,
            cpuDisplayMode: MonitorCpuDisplayMode,
            fixedPosition: Boolean,
            chinese: Boolean,
            accentColor: Int,
            overlayTheme: MonitorOverlayTheme = MonitorOverlayTheme.DARK,
            overlayCustomColor: Int = MonitorOverlayThemeHelper.DEFAULT_CUSTOM_COLOR_RGB,
            overlayOpacity: Float = MonitorOverlayThemeHelper.DEFAULT_OPACITY,
        ) {
            val intent = Intent(context, MonitorOverlayService::class.java).apply {
                action = ACTION_START
                putStringArrayListExtra(EXTRA_METRICS, ArrayList(metrics.map { it.name }))
                putExtra(EXTRA_CPU_DISPLAY_MODE, cpuDisplayMode.name)
                putExtra(EXTRA_FIXED_POSITION, fixedPosition)
                putExtra(EXTRA_CHINESE, chinese)
                putExtra(EXTRA_ACCENT_COLOR, accentColor)
                putExtra(EXTRA_OVERLAY_THEME, overlayTheme.name)
                putExtra(EXTRA_OVERLAY_CUSTOM_COLOR, overlayCustomColor)
                putExtra(EXTRA_OVERLAY_OPACITY, overlayOpacity)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorOverlayService::class.java))
        }
    }
}
