package com.saintm.smartlauncher.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt

/** Bitmaps are generated once per wallpaper selection, not during recomposition or focus movement. */
data class WallpaperAssets(val original: Bitmap, val glass: Bitmap)

data class WallpaperPreset(
    val id: String,
    val name: String,
    val top: Int,
    val bottom: Int,
    val glow: Int,
)

object WallpaperFactory {
    val presets = listOf(
        WallpaperPreset("midnight", "Midnight", Color.rgb(7, 18, 43), Color.rgb(22, 70, 119), Color.rgb(57, 148, 207)),
        WallpaperPreset("lagoon", "Lagoon", Color.rgb(6, 39, 58), Color.rgb(12, 112, 119), Color.rgb(62, 200, 191)),
        WallpaperPreset("ember", "Ember", Color.rgb(44, 19, 36), Color.rgb(143, 57, 54), Color.rgb(245, 139, 79)),
        WallpaperPreset("dawn", "Dawn", Color.rgb(43, 49, 77), Color.rgb(132, 89, 129), Color.rgb(252, 183, 142)),
        WallpaperPreset("forest", "Forest", Color.rgb(12, 37, 40), Color.rgb(33, 91, 70), Color.rgb(119, 170, 113)),
        WallpaperPreset("violet", "Violet", Color.rgb(27, 22, 62), Color.rgb(83, 57, 130), Color.rgb(174, 126, 230)),
        WallpaperPreset("sand", "Sand", Color.rgb(46, 43, 53), Color.rgb(113, 88, 76), Color.rgb(227, 178, 119)),
        WallpaperPreset("glacier", "Glacier", Color.rgb(17, 37, 64), Color.rgb(60, 110, 153), Color.rgb(155, 212, 228)),
        WallpaperPreset("orbit", "Orbit", Color.rgb(12, 17, 32), Color.rgb(48, 51, 82), Color.rgb(122, 116, 202)),
    )

    suspend fun load(
        context: Context,
        wallpaperId: String,
        customUri: String?,
        requestedWidth: Int,
        requestedHeight: Int,
    ): WallpaperAssets = withContext(Dispatchers.IO) {
        val width = requestedWidth.coerceIn(320, 1920)
        val height = requestedHeight.coerceIn(180, 1080)
        val source = customUri?.let { uri -> loadUserImage(context, uri, width, height) }
            ?: makeGradient(presets.firstOrNull { it.id == wallpaperId } ?: presets.first(), width, height)
        val glass = if (Build.VERSION.SDK_INT >= 31) source else makeSoftBlur(source)
        WallpaperAssets(source, glass)
    }

    private fun makeGradient(preset: WallpaperPreset, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        val glowX = width * 0.70f
        val glowY = height * 0.32f
        val glowRadius = max(width, height) * 0.78f
        for (y in 0 until height) {
            val vertical = y.toFloat() / max(1, height - 1)
            val base = mix(preset.top, preset.bottom, vertical)
            for (x in 0 until width) {
                val dx = x - glowX
                val dy = y - glowY
                val distance = sqrt(dx * dx + dy * dy) / glowRadius
                val glowAmount = (1f - distance).coerceIn(0f, 1f) * 0.22f
                pixels[y * width + x] = mix(base, preset.glow, glowAmount)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun loadUserImage(context: Context, rawUri: String, width: Int, height: Int): Bitmap? = runCatching {
        val uri = Uri.parse(rawUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, width, height)
        val decoded = BitmapFactory.Options().run {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, this) }
        } ?: return@runCatching null
        centerCrop(decoded, width, height)
    }.getOrNull()

    private fun calculateSampleSize(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): Int {
        var sample = 1
        while (sourceWidth / sample > width * 2 || sourceHeight / sample > height * 2) sample *= 2
        return sample
    }

    private fun centerCrop(source: Bitmap, width: Int, height: Int): Bitmap {
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
        val scaledWidth = max(width, (source.width * scale).toInt())
        val scaledHeight = max(height, (source.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val cropX = (scaledWidth - width) / 2
        val cropY = (scaledHeight - height) / 2
        val cropped = Bitmap.createBitmap(scaled, cropX, cropY, width, height)
        if (source !== scaled && !source.isRecycled) source.recycle()
        if (cropped !== scaled && !scaled.isRecycled) scaled.recycle()
        return cropped
    }

    /** Low-resolution three-pass box blur for pre-Android 12 glass surfaces. */
    private fun makeSoftBlur(source: Bitmap): Bitmap {
        val smallWidth = max(24, source.width / 16)
        val smallHeight = max(14, source.height / 16)
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        val pixels = IntArray(smallWidth * smallHeight)
        small.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)
        repeat(3) {
            blurPass(pixels, smallWidth, smallHeight, horizontal = true)
            blurPass(pixels, smallWidth, smallHeight, horizontal = false)
        }
        small.setPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)
        val expanded = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        if (small !== expanded) small.recycle()
        return expanded
    }

    private fun blurPass(pixels: IntArray, width: Int, height: Int, horizontal: Boolean) {
        val output = pixels.copyOf()
        val major = if (horizontal) height else width
        val minor = if (horizontal) width else height
        val radius = 2
        for (line in 0 until major) {
            for (point in 0 until minor) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (offset in -radius..radius) {
                    val p = (point + offset).coerceIn(0, minor - 1)
                    val index = if (horizontal) line * width + p else p * width + line
                    val color = pixels[index]
                    a += Color.alpha(color)
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                val index = if (horizontal) line * width + point else point * width + line
                output[index] = Color.argb(a / count, r / count, g / count, b / count)
            }
        }
        output.copyInto(pixels)
    }

    private fun mix(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
        )
    }
}
