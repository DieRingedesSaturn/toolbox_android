package com.example.toolbox.location

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** UI observes one state; provider ownership and stopping stay in the service. */
object MockLocationSession {
    val state = MockLocationService.state

    suspend fun refresh(context: Context) {
        val environment = withContext(Dispatchers.IO) {
            val controller = MockLocationController(context.applicationContext)
            MockLocationEnvironment(
                access = controller.hasAccess(),
                cleanup = controller.hasPendingCleanup(),
                notifications = MockLocationService.notificationsAvailable(context),
                servicesEnabled = controller.servicesEnabled(),
            )
        }
        MockLocationService.environmentChanged(environment)
    }

    fun start(context: Context, config: MockLocationConfig, chinese: Boolean) {
        if (state.value.busy) return
        MockLocationService.startRequested()
        runCatching { MockLocationService.start(context, config, chinese) }
            .onFailure { MockLocationService.startRejected() }
    }

    fun stop(context: Context) {
        runCatching { MockLocationService.stop(context) }.onFailure { failure(MockLocationFailure.STOP) }
    }

    fun failure(reason: MockLocationFailure) = MockLocationService.reportFailure(reason)
}
