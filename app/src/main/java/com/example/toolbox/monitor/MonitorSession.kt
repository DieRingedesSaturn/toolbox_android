package com.example.toolbox.monitor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal data class MonitorSessionState(val overlayRunning: Boolean = false, val sample: MonitorSample? = null)

/** Service owns sampling while running; the visible page owns it otherwise. */
internal object MonitorSession {
    private val mutableState = MutableStateFlow(MonitorSessionState())
    val state = mutableState.asStateFlow()

    fun started() {
        mutableState.update { it.copy(overlayRunning = true) }
    }

    fun stopped() {
        mutableState.update { it.copy(overlayRunning = false) }
    }

    fun publishOverlay(sample: MonitorSample) {
        mutableState.update { if (it.overlayRunning) it.copy(sample = sample) else it }
    }

    suspend fun observeForeground(context: Context) {
        val reader = MonitorReader(context.applicationContext)
        observeForeground {
            withContext(Dispatchers.IO) { runCatching { reader.read() }.getOrNull() }
        }
    }

    internal suspend fun observeForeground(read: suspend () -> MonitorSample?) {
        state.map { it.overlayRunning }.distinctUntilChanged().collectLatest { overlayRunning ->
            if (!overlayRunning) {
                while (currentCoroutineContext().isActive) {
                    val sample = read()
                    mutableState.update { if (it.overlayRunning) it else it.copy(sample = sample) }
                    delay(1_000L)
                }
            }
        }
    }
}
