package com.example.toolbox.ledger

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

class LedgerNotificationListener : NotificationListenerService() {
    /**
     * Notification callbacks arrive on the main thread; parsing, the
     * suggestion write, and any auto-book SQLite write run on this worker so
     * the system is never kept waiting.
     */
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ledger-notification").apply { isDaemon = true }
    }

    override fun onListenerConnected() {
        if (!NotificationLedgerStore(applicationContext).enabled()) {
            runCatching { setActive(applicationContext, false) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val input = runCatching { NotificationLedgerStore.inputOf(sbn) }.getOrNull() ?: return
        worker.execute {
            runCatching {
                val candidate = NotificationLedgerStore(applicationContext).record(input)
                    ?: return@runCatching
                NotificationAutoBooker.book(applicationContext, candidate)
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        fun setActive(context: Context, active: Boolean) {
            val component = ComponentName(context, LedgerNotificationListener::class.java)
            context.packageManager.setComponentEnabledSetting(
                component,
                if (active) {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
            if (active) runCatching { requestRebind(component) }
        }
    }
}
