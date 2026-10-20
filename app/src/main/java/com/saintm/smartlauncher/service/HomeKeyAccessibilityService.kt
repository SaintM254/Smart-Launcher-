package com.saintm.smartlauncher.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.saintm.smartlauncher.data.LauncherPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Filters only KEYCODE_HOME. No accessibility window or text data is requested or read. */
class HomeKeyAccessibilityService : AccessibilityService() {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences by lazy { LauncherPreferences(this) }
    @Volatile private var overrideHome = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
        }
        work.launch {
            preferences.settings.collect { settings ->
                overrideHome = settings.homeOverrideEnabled && !settings.safeMode
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (!overrideHome) return false
        if (event.action == KeyEvent.ACTION_UP) {
            runCatching {
                startActivity(
                    Intent(this, com.saintm.smartlauncher.ui.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    },
                )
            }
        }
        // Consume only Home while the user-enabled override is active.
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        work.cancel()
        super.onDestroy()
    }
}
