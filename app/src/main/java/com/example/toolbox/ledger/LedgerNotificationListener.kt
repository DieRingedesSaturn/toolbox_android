package com.example.toolbox.ledger

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class LedgerNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        if (!NotificationLedgerStore(applicationContext).enabled()) {
            runCatching { setActive(applicationContext, false) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { NotificationLedgerStore(applicationContext).record(sbn) }
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
