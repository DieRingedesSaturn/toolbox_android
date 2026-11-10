package com.example.toolbox.totp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.scale
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

object TotpQrDecoder {
    private val hints: Map<DecodeHintType, Any> = EnumMap<DecodeHintType, Any>(
        DecodeHintType::class.java,
    ).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        put(DecodeHintType.CHARACTER_SET, "UTF-8")
        put(DecodeHintType.TRY_HARDER, true)
    }

    fun decodeYuv(yBytes: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || yBytes.size < width * height) return null
        val source = PlanarYUVLuminanceSource(
            yBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false,
        )
        return decodeLuminanceSource(source)
    }

    fun decodeBitmap(bitmap: Bitmap): String? {
        val scaled = scaleDownIfNeeded(bitmap, maxDimension = 1280)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        return decodeLuminanceSource(source)
    }

    fun decodeImageUri(context: Context, uri: Uri): String? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
        if (maxDim <= 0) return null
        var sampleSize = 1
        while (maxDim / sampleSize > 1600) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null
        return try {
            decodeBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeLuminanceSource(source: LuminanceSource): String? {
        val reader = QRCodeReader()
        val candidates = listOf(
            BinaryBitmap(HybridBinarizer(source)),
            BinaryBitmap(GlobalHistogramBinarizer(source)),
            BinaryBitmap(HybridBinarizer(source.invert())),
        )
        for (candidate in candidates) {
            val text = runCatching {
                reader.decode(candidate, hints).text
            }.getOrNull()
            reader.reset()
            if (!text.isNullOrBlank()) return text.trim()
        }
        return null
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return bitmap.scale(targetW, targetH)
    }
}

@Composable
fun TotpCameraScannerView(
    modifier: Modifier = Modifier,
    onQrCodeScanned: (String) -> Unit,
    onCameraError: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentOnScanned by rememberUpdatedState(onQrCodeScanned)
    val currentOnError by rememberUpdatedState(onCameraError)
    val controller = remember(context) {
        Camera2QrController(
            context = context.applicationContext,
            onResult = { text -> currentOnScanned(text) },
            onError = { currentOnError() },
        )
    }

    DisposableEffect(controller) {
        onDispose {
            controller.stop()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextureView(viewContext).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        controller.attachAndStart(this@apply, width, height)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        controller.updateTransform(this@apply, width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        controller.stop()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
    )
}

private class Camera2QrController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSize: Size = Size(1280, 720)
    private var sensorOrientation: Int = 90
    private val delivered = AtomicBoolean(false)
    private val decodingFrame = AtomicBoolean(false)
    @Volatile
    private var lastDecodeAttemptMs = 0L

    @SuppressLint("MissingPermission")
    fun attachAndStart(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        stop()
        delivered.set(false)
        val thread = HandlerThread("TotpQrCamera").also { it.start() }
        val handler = Handler(thread.looper)
        bgThread = thread
        bgHandler = handler

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (manager == null) {
            mainHandler.post { onError() }
            return
        }

        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull()
        }.getOrNull()

        if (cameraId == null) {
            mainHandler.post { onError() }
            return
        }

        runCatching {
            val chars = manager.getCameraCharacteristics(cameraId)
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val surfaceChoices = map?.getOutputSizes(SurfaceTexture::class.java)
            previewSize = chooseOptimalSize(surfaceChoices, 1280, 720)
            updateTransform(textureView, viewWidth, viewHeight)

            val analysisChoices = map?.getOutputSizes(ImageFormat.YUV_420_888)
            val analysisSize = chooseOptimalSize(analysisChoices, 960, 720)
            val reader = ImageReader.newInstance(
                analysisSize.width,
                analysisSize.height,
                ImageFormat.YUV_420_888,
                2,
            )
            reader.setOnImageAvailableListener({ ir ->
                val image = runCatching { ir.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                processImage(image)
            }, handler)
            imageReader = reader

            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        startPreviewSession(camera, textureView, reader, handler)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        runCatching { camera.close() }
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        runCatching { camera.close() }
                        cameraDevice = null
                        mainHandler.post { onError() }
                    }
                },
                handler,
            )
        }.onFailure {
            mainHandler.post { onError() }
        }
    }

    private fun startPreviewSession(
        camera: CameraDevice,
        textureView: TextureView,
        reader: ImageReader,
        handler: Handler,
    ) {
        val surfaceTexture = textureView.surfaceTexture ?: return
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(surfaceTexture)
        val analysisSurface = reader.surface

        runCatching {
            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                addTarget(analysisSurface)
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
            }
            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(previewSurface, analysisSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        runCatching {
                            session.setRepeatingRequest(requestBuilder.build(), null, handler)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        mainHandler.post { onError() }
                    }
                },
                handler,
            )
        }.onFailure {
            mainHandler.post { onError() }
        }
    }

    private fun processImage(image: Image) {
        try {
            if (delivered.get()) return
            val now = System.currentTimeMillis()
            if (now - lastDecodeAttemptMs < 140L) return
            if (!decodingFrame.compareAndSet(false, true)) return
            lastDecodeAttemptMs = now
            try {
                val width = image.width
                val height = image.height
                val yPlane = image.planes.firstOrNull() ?: return
                val buffer = yPlane.buffer
                val rowStride = yPlane.rowStride
                val yBytes = ByteArray(width * height)
                if (rowStride == width) {
                    buffer.get(yBytes, 0, width * height)
                } else {
                    for (row in 0 until height) {
                        buffer.position(row * rowStride)
                        buffer.get(yBytes, row * width, width)
                    }
                }
                val text = TotpQrDecoder.decodeYuv(yBytes, width, height)
                if (!text.isNullOrBlank() && delivered.compareAndSet(false, true)) {
                    mainHandler.post { onResult(text) }
                }
            } finally {
                decodingFrame.set(false)
            }
        } finally {
            runCatching { image.close() }
        }
    }

    fun updateTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val isSwapped = sensorOrientation == 90 || sensorOrientation == 270
        val bufferW = if (isSwapped) previewSize.height.toFloat() else previewSize.width.toFloat()
        val bufferH = if (isSwapped) previewSize.width.toFloat() else previewSize.height.toFloat()
        if (bufferW <= 0f || bufferH <= 0f) return

        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
        val bufferRatio = bufferW / bufferH
        val scaleX: Float
        val scaleY: Float
        if (viewRatio > bufferRatio) {
            scaleX = 1f
            scaleY = viewRatio / bufferRatio
        } else {
            scaleX = bufferRatio / viewRatio
            scaleY = 1f
        }
        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        }
        mainHandler.post {
            textureView.setTransform(matrix)
        }
    }

    fun stop() {
        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
        runCatching { imageReader?.close() }
        imageReader = null
        bgThread?.quitSafely()
        bgThread = null
        bgHandler = null
    }

    private fun chooseOptimalSize(choices: Array<out Size>?, targetW: Int, targetH: Int): Size {
        if (choices.isNullOrEmpty()) return Size(targetW, targetH)
        return choices.minByOrNull { size ->
            kotlin.math.abs(size.width - targetW) + kotlin.math.abs(size.height - targetH)
        } ?: choices.first()
    }
}
