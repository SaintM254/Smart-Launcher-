package com.saintm.smartlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** A small process-local cache keeps package-manager icon work off the scrolling path. */
class InstalledAppsRepository(context: Context) {
    private val context = context.applicationContext
    private val packageManager = this.context.packageManager

    suspend fun loadApps(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val candidates = LinkedHashMap<String, ResolveInfo>()
        query(Intent.CATEGORY_LAUNCHER).forEach { info ->
            info.activityInfo?.packageName?.let { candidates.putIfAbsent(it, info) }
        }
        // Prefer a TV banner when a package provides a LEANBACK_LAUNCHER activity.
        query(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach { info ->
            info.activityInfo?.packageName?.let { candidates[it] = info }
        }

        candidates.mapNotNull { (packageName, info) ->
            if (packageName == context.packageName) return@mapNotNull null
            val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            val label = runCatching { packageManager.getApplicationLabel(appInfo).toString() }
                .getOrDefault(packageName)
                .ifBlank { packageName }
            val fallback = runCatching { appInfo.loadIcon(packageManager) }.getOrNull() ?: return@mapNotNull null
            LauncherApp(packageName, label, cachedIcon(packageName, info, fallback))
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun launch(packageName: String): Boolean {
        val intent = runCatching { packageManager.getLeanbackLaunchIntentForPackage(packageName) }.getOrNull()
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun appInfoIntent(packageName: String): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun uninstallIntent(packageName: String): Intent = Intent(
        Intent.ACTION_DELETE,
        Uri.parse("package:$packageName"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun loadCustomIcon(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val imageUri = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / (sample * 2) > 256 && bounds.outHeight / (sample * 2) > 256) sample *= 2
            val decoded = context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            } ?: return@withContext null
            val scale = min(1f, 256f / max(decoded.width, decoded.height))
            if (scale == 1f) decoded else Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        }.getOrNull()
    }

    private fun query(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    private fun cachedIcon(packageName: String, info: ResolveInfo, fallback: Drawable): Bitmap {
        iconCache.get(packageName)?.let { return it }
        val source = runCatching { info.loadBanner(packageManager) }.getOrNull() ?: fallback
        val sourceBitmap = runCatching {
            val width = source.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(1024) ?: 192
            val height = source.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(1024) ?: 192
            source.toBitmap(width, height, Bitmap.Config.ARGB_8888)
        }.getOrElse { fallback.toBitmap(192, 192, Bitmap.Config.ARGB_8888) }
        val output = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val scale = min(192f / sourceBitmap.width, 192f / sourceBitmap.height)
        val width = sourceBitmap.width * scale
        val height = sourceBitmap.height * scale
        val left = (192f - width) / 2f
        val top = (192f - height) / 2f
        Canvas(output).drawBitmap(sourceBitmap, null, RectF(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        if (sourceBitmap !== output && !sourceBitmap.isRecycled) sourceBitmap.recycle()
        iconCache.put(packageName, output)
        return output
    }

    companion object {
        private val iconCache = object : LruCache<String, Bitmap>(72) {
            override fun sizeOf(key: String, value: Bitmap): Int = 1
            // Do not recycle evicted values: a visible Compose item may still be painting that bitmap.
        }

        fun invalidateIconCache(packageName: String? = null) {
            if (packageName == null) iconCache.evictAll() else iconCache.remove(packageName)
        }
    }
}

data class LauncherApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap,
)
