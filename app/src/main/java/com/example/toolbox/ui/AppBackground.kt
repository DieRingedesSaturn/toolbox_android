package com.example.toolbox.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Stores one user-picked background image in internal storage and samples its dominant colour. */
object AppBackground {
    private const val FILE_NAME = "background.img"
    private const val MAX_EDGE = 2048
    private const val SAMPLE_EDGE = 64
    private const val BLUR_MAX_EDGE = 320
    private const val BLUR_RADIUS = 14
    private const val BLUR_PASSES = 3
    private const val HUE_BUCKETS = 24

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).isFile

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Decodes, downscales and stores [uri]; returns the sampled seed RGB (0xRRGGBB) or null. */
    fun import(context: Context, uri: Uri): Int? {
        val target = file(context)
        val stored = runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            val decoded = resolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
            val scaled = decoded.scaledToMaxEdge()
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            scaled.recycle()
            target
        }.getOrNull() ?: run {
            target.delete()
            return null
        }

        return runCatching {
            val sample = BitmapFactory.decodeFile(stored.path) ?: return null
            val small = sample.scaledToMaxEdge(SAMPLE_EDGE)
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            small.recycle()
            dominantSeed(pixels)
        }.getOrNull()
    }

    fun load(context: Context): Bitmap? = decode(file(context), 0)

    /** Smaller decode for on-screen previews, so a full-size bitmap is not held twice. */
    fun loadPreview(context: Context, maxEdge: Int = 720): Bitmap? = decode(file(context), maxEdge)

    /** Produces a downscaled, Gaussian-approximated blurred copy of [source] for acrylic cards. */
    fun blurForBackdrop(source: Bitmap, strength: Float = 0.6f): Bitmap {
        val clamped = strength.coerceIn(0f, 1f)
        if (clamped <= 0.01f) return source
        val radius = ((clamped * 23f) + 1f).toInt()
        val longest = max(source.width, source.height).coerceAtLeast(1)
        val ratio = (BLUR_MAX_EDGE.toFloat() / longest).coerceAtMost(1f)
        val targetW = (source.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (source.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        val working = if (scaled === source) {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            scaled
        }
        val pixels = IntArray(targetW * targetH)
        working.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        boxBlurPixels(pixels, targetW, targetH, radius, BLUR_PASSES)
        working.setPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        return working
    }

    /** Separable multi-pass box blur approximating Gaussian blur on ARGB [pixels] in-place. */
    internal fun boxBlurPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        passes: Int = 3,
    ) {
        if (width <= 1 || height <= 1 || radius <= 0 || passes <= 0) return
        val scratch = IntArray(pixels.size)
        val window = radius * 2 + 1
        repeat(passes) {
            // Horizontal pass: pixels -> scratch
            for (y in 0 until height) {
                val rowOffset = y * width
                var sumA = 0
                var sumR = 0
                var sumG = 0
                var sumB = 0
                for (dx in -radius..radius) {
                    val c = pixels[rowOffset + dx.coerceIn(0, width - 1)]
                    sumA += (c ushr 24) and 0xFF
                    sumR += (c shr 16) and 0xFF
                    sumG += (c shr 8) and 0xFF
                    sumB += c and 0xFF
                }
                for (x in 0 until width) {
                    scratch[rowOffset + x] =
                        ((sumA / window) shl 24) or
                        ((sumR / window) shl 16) or
                        ((sumG / window) shl 8) or
                        (sumB / window)
                    val outIdx = rowOffset + (x - radius).coerceIn(0, width - 1)
                    val inIdx = rowOffset + (x + radius + 1).coerceIn(0, width - 1)
                    val outC = pixels[outIdx]
                    val inC = pixels[inIdx]
                    sumA += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
                    sumR += ((inC shr 16) and 0xFF) - ((outC shr 16) and 0xFF)
                    sumG += ((inC shr 8) and 0xFF) - ((outC shr 8) and 0xFF)
                    sumB += (inC and 0xFF) - (outC and 0xFF)
                }
            }
            // Vertical pass: scratch -> pixels
            for (x in 0 until width) {
                var sumA = 0
                var sumR = 0
                var sumG = 0
                var sumB = 0
                for (dy in -radius..radius) {
                    val c = scratch[dy.coerceIn(0, height - 1) * width + x]
                    sumA += (c ushr 24) and 0xFF
                    sumR += (c shr 16) and 0xFF
                    sumG += (c shr 8) and 0xFF
                    sumB += c and 0xFF
                }
                for (y in 0 until height) {
                    pixels[y * width + x] =
                        ((sumA / window) shl 24) or
                        ((sumR / window) shl 16) or
                        ((sumG / window) shl 8) or
                        (sumB / window)
                    val outIdx = (y - radius).coerceIn(0, height - 1) * width + x
                    val inIdx = (y + radius + 1).coerceIn(0, height - 1) * width + x
                    val outC = scratch[outIdx]
                    val inC = scratch[inIdx]
                    sumA += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
                    sumR += ((inC shr 16) and 0xFF) - ((outC shr 16) and 0xFF)
                    sumG += ((inC shr 8) and 0xFF) - ((outC shr 8) and 0xFF)
                    sumB += (inC and 0xFF) - (outC and 0xFF)
                }
            }
        }
    }

    private fun decode(file: File, maxEdge: Int): Bitmap? = runCatching {
        if (!file.isFile) return null
        val options = BitmapFactory.Options()
        if (maxEdge > 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) {
                    sample *= 2
                }
                options.inSampleSize = sample
            }
        }
        BitmapFactory.decodeFile(file.path, options)
    }.getOrNull()

    /** Dominant, reasonably saturated colour of [pixels] (ARGB ints). Pure, unit-testable. */
    fun dominantSeed(pixels: IntArray): Int {
        val counts = IntArray(HUE_BUCKETS)
        val scores = FloatArray(HUE_BUCKETS)
        val redSums = LongArray(HUE_BUCKETS)
        val greenSums = LongArray(HUE_BUCKETS)
        val blueSums = LongArray(HUE_BUCKETS)
        var opaqueCount = 0
        var opaqueRed = 0L
        var opaqueGreen = 0L
        var opaqueBlue = 0L

        pixels.forEach { pixel ->
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 128) return@forEach
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            opaqueCount++
            opaqueRed += red
            opaqueGreen += green
            opaqueBlue += blue

            val maxChannel = max(red, max(green, blue))
            val minChannel = minOf(red, minOf(green, blue))
            val value = maxChannel / 255f
            if (value < 0.12f || value > 0.96f) return@forEach
            val saturation = if (maxChannel == 0) 0f else (maxChannel - minChannel) / maxChannel.toFloat()
            if (saturation < 0.10f) return@forEach

            val bucket = hueBucket(red, green, blue, maxChannel, minChannel)
            counts[bucket]++
            scores[bucket] += 0.25f + saturation
            redSums[bucket] += red
            greenSums[bucket] += green
            blueSums[bucket] += blue
        }

        var best = -1
        var bestScore = 0f
        scores.forEachIndexed { index, score ->
            if (counts[index] > 0 && score > bestScore) {
                bestScore = score
                best = index
            }
        }
        if (best >= 0) {
            val count = counts[best]
            return rgb(
                (redSums[best] / count).toInt(),
                (greenSums[best] / count).toInt(),
                (blueSums[best] / count).toInt(),
            )
        }
        if (opaqueCount > 0) {
            return rgb(
                (opaqueRed / opaqueCount).toInt(),
                (opaqueGreen / opaqueCount).toInt(),
                (opaqueBlue / opaqueCount).toInt(),
            )
        }
        return 0x808080
    }

    /** Largest power-of-two subsample that still keeps both edges above [MAX_EDGE]. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (max(width / (sample * 2), height / (sample * 2)) >= MAX_EDGE / 2) {
            sample *= 2
        }
        return sample
    }

    /** Downscales to [maxEdge] when needed; the source bitmap is recycled once replaced. */
    private fun Bitmap.scaledToMaxEdge(maxEdge: Int = MAX_EDGE): Bitmap {
        val longest = max(width, height)
        if (longest <= maxEdge) return this
        val ratio = maxEdge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            this,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== this) recycle()
        return scaled
    }

    private fun hueBucket(red: Int, green: Int, blue: Int, maxChannel: Int, minChannel: Int): Int {
        val delta = maxChannel - minChannel
        if (delta == 0) return 0
        val hue = when (maxChannel) {
            red -> 60f * ((green - blue) / delta.toFloat()).mod(6f)
            green -> 60f * ((blue - red) / delta.toFloat() + 2f)
            else -> 60f * ((red - green) / delta.toFloat() + 4f)
        }
        return ((hue / 360f * HUE_BUCKETS).toInt()).coerceIn(0, HUE_BUCKETS - 1)
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        ((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF)
}
