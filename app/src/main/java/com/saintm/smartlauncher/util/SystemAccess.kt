package com.saintm.smartlauncher.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.saintm.smartlauncher.service.HomeKeyAccessibilityService
import com.saintm.smartlauncher.service.MediaNotificationListener

fun isHomeKeyServiceEnabled(context: Context): Boolean = enabledServices(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    .any { it == ComponentName(context, HomeKeyAccessibilityService::class.java) }

fun isMediaListenerEnabled(context: Context): Boolean = enabledServices(context, "enabled_notification_listeners")
    .any { it == ComponentName(context, MediaNotificationListener::class.java) }

fun isDefaultHome(context: Context): Boolean {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    return resolved?.activityInfo?.packageName == context.packageName
}

fun canOpenTvInputs(context: Context): Boolean = Intent("android.settings.TV_INPUT_SETTINGS")
    .resolveActivity(context.packageManager) != null

fun canOpenAssistant(context: Context): Boolean = Intent(Intent.ACTION_ASSIST)
    .resolveActivity(context.packageManager) != null

private fun enabledServices(context: Context, settingName: String): List<ComponentName> {
    val raw = Settings.Secure.getString(context.contentResolver, settingName).orEmpty()
    return raw.split(':').mapNotNull { ComponentName.unflattenFromString(it) }
}
