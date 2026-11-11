package com.example.toolbox

import android.app.Application
import com.example.toolbox.ledger.NotificationBookkeeping

class ToolboxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationBookkeeping.enforce(this)
    }
}
