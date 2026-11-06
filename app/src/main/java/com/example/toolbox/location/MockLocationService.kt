package com.example.toolbox.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.toolbox.MainActivity
import com.example.toolbox.R
import com.example.toolbox.ui.AppLanguage
import com.example.toolbox.ui.ToolboxStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ACTION_OPEN_MOCK_LOCATION = "com.example.toolbox.location.OPEN_MOCK"

/** Explicit, bounded testing session. Never restarted by Android or started at boot. */
class MockLocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null
    private var sessionStrings: ToolboxStrings? = null
    private var sessionConfig: MockLocationConfig? = null
    private var sessionEndElapsed: Long = 0L
    private var sessionEndWall: Long = 0L
    private var lastNotifiedRemainingMinutes: Int = -1

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || session != null) {
            if (session == null) stopSelf()
            return START_NOT_STICKY
        }
        val config = MockLocationConfig(
            intent.getDoubleExtra("latitude", Double.NaN),
            intent.getDoubleExtra("longitude", Double.NaN),
            intent.getFloatExtra("accuracy", Float.NaN),
            intent.getIntExtra("minutes", 0),
        )
        if (!config.isValid()) {
            mutableState.value = MockLocationState(MockLocationPhase.ERROR, failure = MockLocationFailure.START)
            stopSelf()
            return START_NOT_STICKY
        }
        val strings = ToolboxStrings(if (intent.getBooleanExtra("chinese", false)) AppLanguage.CHINESE else AppLanguage.ENGLISH)
        sessionStrings = strings
        sessionConfig = config
        sessionEndElapsed = SystemClock.elapsedRealtime() + config.durationMinutes * 60_000L
        sessionEndWall = System.currentTimeMillis() + config.durationMinutes * 60_000L
        lastNotifiedRemainingMinutes = -1
        try {
            startStatusNotification(strings, config)
        } catch (_: Exception) {
            mutableState.value = MockLocationState(MockLocationPhase.ERROR, failure = MockLocationFailure.NOTIFICATIONS)
            stopSelf()
            return START_NOT_STICKY
        }
        mutableState.value = MockLocationState(MockLocationPhase.STARTING)
        // Even an immediate Stop must enter finally and remove any test providers.
        session = scope.launch(start = CoroutineStart.ATOMIC) {
            var controller: MockLocationController? = null
            var failure: MockLocationFailure? = null
            try {
                currentCoroutineContext().ensureActive()
                val activeController = MockLocationController(this@MockLocationService)
                controller = activeController
                if (!activeController.hasAccess()) {
                    failure = MockLocationFailure.ACCESS
                    return@launch
                }
                if (!activeController.servicesEnabled()) {
                    failure = MockLocationFailure.SERVICES_DISABLED
                    return@launch
                }
                activeController.start(config)
                mutableState.value = MockLocationState(
                    MockLocationPhase.RUNNING, config, sessionEndWall,
                )
                while (isActive && SystemClock.elapsedRealtime() < sessionEndElapsed) {
                    if (!activeController.servicesEnabled()) {
                        failure = MockLocationFailure.SERVICES_DISABLED
                        break
                    }
                    if (!notificationsAvailable(this@MockLocationService)) {
                        failure = MockLocationFailure.NOTIFICATIONS
                        break
                    }
                    activeController.publish(config)
                    refreshStatusNotification()
                    delay(1_000L)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                failure = MockLocationFailure.ACCESS
            } catch (_: Exception) {
                failure = MockLocationFailure.PROVIDER
            } finally {
                withContext(NonCancellable) {
                    mutableState.value = MockLocationState(MockLocationPhase.STOPPING)
                    val cleaned = runCatching {
                        (controller ?: MockLocationController(this@MockLocationService)).stop()
                    }.getOrDefault(false)
                    withContext(Dispatchers.Main) {
                        session = null
                        mutableState.value = MockLocationState(
                            when {
                                !cleaned -> MockLocationPhase.CLEANUP_REQUIRED
                                failure != null -> MockLocationPhase.ERROR
                                else -> MockLocationPhase.STOPPED
                            },
                            failure = failure,
                        )
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopSession() {
        mutableState.value = MockLocationState(MockLocationPhase.STOPPING)
        val current = session
        if (current != null) current.cancel() else {
            session = scope.launch {
                val cleaned = runCatching { MockLocationController(this@MockLocationService).stop() }.getOrDefault(false)
                mutableState.value = MockLocationState(
                    if (cleaned) MockLocationPhase.STOPPED else MockLocationPhase.CLEANUP_REQUIRED,
                )
                withContext(Dispatchers.Main) {
                    session = null
                    stopSelf()
                }
            }
        }
    }

    private fun remainingMinutes(): Int {
        val left = sessionEndElapsed - SystemClock.elapsedRealtime()
        if (left <= 0L) return 0
        return ((left + 59_999L) / 60_000L).toInt()
    }

    private fun startStatusNotification(strings: ToolboxStrings, config: MockLocationConfig) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, strings.mockLocationTitle, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        check(notificationsAvailable(this))
        val remaining = remainingMinutes().coerceAtLeast(1)
        lastNotifiedRemainingMinutes = remaining
        val notification = buildStatusNotification(strings, config, remaining)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun refreshStatusNotification() {
        val remaining = remainingMinutes()
        if (remaining == lastNotifiedRemainingMinutes) return
        val strings = sessionStrings ?: return
        val config = sessionConfig ?: return
        lastNotifiedRemainingMinutes = remaining
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildStatusNotification(strings, config, remaining))
    }

    private fun buildStatusNotification(
        strings: ToolboxStrings,
        config: MockLocationConfig,
        remainingMinutes: Int,
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_module_location)
        .setContentTitle(strings.mockLocationOn)
        .setContentText(
            strings.mockLocationNotification(
                formatShortCoordinates(config.latitude, config.longitude),
                remainingMinutes,
            ),
        )
        .setContentIntent(
            PendingIntent.getActivity(
                this, 7102,
                Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_MOCK_LOCATION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setWhen(sessionEndWall)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .addAction(
            0,
            strings.mockLocationStop,
            PendingIntent.getService(
                this, 7101,
                Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSession()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.example.toolbox.location.START_MOCK"
        private const val ACTION_STOP = "com.example.toolbox.location.STOP_MOCK"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 7100
        private val mutableState = MutableStateFlow(MockLocationState())
        val state = mutableState.asStateFlow()

        fun notificationsAvailable(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                context.getSystemService(NotificationManager::class.java)
                    .getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

        fun start(context: Context, config: MockLocationConfig, chinese: Boolean) {
            require(config.isValid())
            ContextCompat.startForegroundService(context, Intent(context, MockLocationService::class.java)
                .setAction(ACTION_START)
                .putExtra("latitude", config.latitude).putExtra("longitude", config.longitude)
                .putExtra("accuracy", config.accuracyMeters).putExtra("minutes", config.durationMinutes)
                .putExtra("chinese", chinese))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MockLocationService::class.java).setAction(ACTION_STOP))
        }
    }
}
