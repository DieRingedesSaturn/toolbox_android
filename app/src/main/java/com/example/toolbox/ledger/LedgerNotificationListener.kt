package com.example.toolbox.ledger

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class LedgerNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { NotificationLedgerStore(applicationContext).record(sbn) }
    }
}
