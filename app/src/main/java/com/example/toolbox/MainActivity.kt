package com.example.toolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.toolbox.ui.ToolboxApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestSmoothRefreshRate()
        setContent {
            ToolboxApp()
        }
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = maxMode.modeId
                }
            } else {
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = maxMode.refreshRate
                }
            }
        }
    }
}
