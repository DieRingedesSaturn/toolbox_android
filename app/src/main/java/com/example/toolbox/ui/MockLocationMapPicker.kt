package com.example.toolbox.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlinx.coroutines.delay

private const val MAP_READY_TIMEOUT_MS = 15_000L
private const val MAP_PAGE = "file:///android_asset/mockmap/index.html"
private const val MAP_BRIDGE_NAME = "ToolboxMap"
private const val TOOLBOX_USER_AGENT = " Toolbox/0.1"

internal fun formatPickedCoordinate(value: Double): String =
    String.format(Locale.US, "%.6f", value)

internal fun parseMapCoordinate(text: String, min: Double, max: Double): Double? {
    val value = text.trim().toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() && it in min..max }
}

internal fun mockMapAssetUrl(
    latitude: Double?,
    longitude: Double?,
    dark: Boolean,
): String {
    val startLat = latitude?.takeIf { it.isFinite() && it in -90.0..90.0 }
    val startLng = longitude?.takeIf { it.isFinite() && it in -180.0..180.0 }
    val params = buildList {
        if (startLat != null && startLng != null) {
            add("lat=${formatPickedCoordinate(startLat)}")
            add("lng=${formatPickedCoordinate(startLng)}")
        }
        if (dark) add("dark=1")
    }
    return if (params.isEmpty()) MAP_PAGE else "$MAP_PAGE?${params.joinToString("&")}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MockLocationMapDialog(
    strings: ToolboxStrings,
    initialLatitude: Double?,
    initialLongitude: Double?,
    onConfirm: (latitude: Double, longitude: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasStart = initialLatitude != null && initialLongitude != null
    val startLatitude = if (hasStart) initialLatitude else null
    val startLongitude = if (hasStart) initialLongitude else null
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var reloadToken by remember { mutableIntStateOf(0) }
    var mapReady by remember { mutableStateOf(false) }
    var mapFailed by remember { mutableStateOf(false) }
    var pickedLatitude by remember { mutableStateOf(startLatitude) }
    var pickedLongitude by remember { mutableStateOf(startLongitude) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val pageUrl = remember(startLatitude, startLongitude, dark) {
        mockMapAssetUrl(startLatitude, startLongitude, dark)
    }
    val selectedLatitude = pickedLatitude
    val selectedLongitude = pickedLongitude
    val canConfirm = selectedLatitude != null && selectedLongitude != null
    val subtitle = if (selectedLatitude != null && selectedLongitude != null) {
        "${formatPickedCoordinate(selectedLatitude)}, ${formatPickedCoordinate(selectedLongitude)}"
    } else {
        strings.mockLocationMapTapToPick
    }

    LaunchedEffect(reloadToken, pageUrl) {
        mapReady = false
        mapFailed = false
        delay(MAP_READY_TIMEOUT_MS)
        if (!mapReady) mapFailed = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                topBar = {
                    ToolboxTopBar(
                        title = strings.mockLocationPickOnMap,
                        onBack = onDismiss,
                        backLabel = strings.back,
                        subtitle = subtitle,
                        actions = {
                            TextButton(
                                enabled = canConfirm,
                                onClick = {
                                    val latitude = selectedLatitude ?: return@TextButton
                                    val longitude = selectedLongitude ?: return@TextButton
                                    onConfirm(latitude, longitude)
                                },
                            ) {
                                Text(strings.mockLocationMapConfirm)
                            }
                        },
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    key(reloadToken, pageUrl) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                createMockMapWebView(
                                    context = context,
                                    url = pageUrl,
                                    bridge = MockMapBridge(
                                        handler = handler,
                                        onPick = { latitude, longitude ->
                                            pickedLatitude = latitude
                                            pickedLongitude = longitude
                                        },
                                        onReady = { mapReady = true },
                                        onFail = { mapFailed = true },
                                    ),
                                )
                            },
                            onRelease = { view ->
                                view.stopLoading()
                                view.removeJavascriptInterface(MAP_BRIDGE_NAME)
                                view.destroy()
                            },
                        )
                    }
                    if (!mapReady && !mapFailed) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Text(strings.mockLocationMapLoading)
                        }
                    }
                    if (mapFailed && !mapReady) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                strings.mockLocationMapFailed,
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = { reloadToken += 1 }) {
                                Text(strings.mockLocationMapRetry)
                            }
                        }
                    }
                    Text(
                        strings.mockLocationMapAttribution,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private class MockMapBridge(
    private val handler: Handler,
    private val onPick: (Double, Double) -> Unit,
    private val onReady: () -> Unit,
    private val onFail: () -> Unit,
) {
    @JavascriptInterface
    fun pick(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return
        handler.post { onPick(latitude, longitude) }
    }

    @JavascriptInterface
    fun ready() {
        handler.post(onReady)
    }

    @JavascriptInterface
    fun fail(reason: String?) {
        handler.post(onFail)
    }
}

/**
 * Loads the bundled picker over `file:///android_asset/`. OpenFreeMap styles are
 * HTTPS with CORS `*`. If a vendor WebView blocks `Origin: null`, switch this
 * page to `WebViewAssetLoader` rather than enabling universal file access.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createMockMapWebView(
    context: android.content.Context,
    url: String,
    bridge: MockMapBridge,
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = settings.userAgentString + TOOLBOX_USER_AGENT
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        overScrollMode = View.OVER_SCROLL_NEVER
        addJavascriptInterface(bridge, MAP_BRIDGE_NAME)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val target = request.url
                val assetPage = target.scheme == "file" &&
                    (target.path?.startsWith("/android_asset/mockmap/") == true)
                return !assetPage
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) bridge.fail("load")
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                bridge.fail("crash")
                return true
            }
        }
        loadUrl(url)
    }
}
