package com.example.toolbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.toolbox.ui.ToolboxApp

class MainActivity : ComponentActivity() {

    private var launchAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestSmoothRefreshRate()
        if (savedInstanceState == null) {
            launchAction = intent?.action
        }
        setContent {
            ToolboxApp(
                launchAction = launchAction,
                onLaunchActionConsumed = { launchAction = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchAction = intent.action
    }

    @Suppress("DEPRECATION")
    private fun requestSmoothRefreshRate() {
        val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        }
        val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
        if (maxMode != null) {
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = maxMode.modeId
            }
        }
    }
}
