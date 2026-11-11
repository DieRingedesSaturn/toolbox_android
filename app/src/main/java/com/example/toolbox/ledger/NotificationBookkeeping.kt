package com.example.toolbox.ledger

import android.content.Context

/**
 * Notification quick entry stays compiled so it can be turned back on, but it
 * does not run while [ENABLED] is false. Flip the flag, then the existing
 * ledger menu and listener toggle work again. The manifest also ships the
 * listener disabled; [LedgerNotificationListener.setActive] enables the
 * component only after the user opts in.
 */
object NotificationBookkeeping {
    const val ENABLED: Boolean = false

    /**
     * Turns the listener off, drops the enabled preference, and strips any
     * notification body text already stored in SharedPreferences.
     */
    fun enforce(context: Context) {
        if (ENABLED) return
        val appContext = context.applicationContext
        val store = NotificationLedgerStore(appContext)
        store.redactStoredBodies()
        if (store.enabled()) {
            store.setEnabled(false)
        } else {
            runCatching { LedgerNotificationListener.setActive(appContext, false) }
        }
        store.redactStoredBodies()
    }
}
