package com.saintm.smartlauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberSaveable
import com.saintm.smartlauncher.data.InstalledAppsRepository
import com.saintm.smartlauncher.data.LauncherApp
import com.saintm.smartlauncher.data.LauncherPreferences
import com.saintm.smartlauncher.data.LauncherSettings

class MainActivity : ComponentActivity() {
    private val preferences by lazy { LauncherPreferences(this) }
    private val appsRepository by lazy { InstalledAppsRepository(this) }
    private var packageEpoch by mutableIntStateOf(0)
    private var packageReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartTheme {
                LauncherRoot(
                    activity = this,
                    preferences = preferences,
                    appsRepository = appsRepository,
                    packageEpoch = packageEpoch,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        packageEpoch++
        if (packageReceiver == null) {
            packageReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    InstalledAppsRepository.invalidateIconCache(intent?.data?.schemeSpecificPart)
                    packageEpoch++
                }
            }.also { receiver ->
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addDataScheme("package")
                }
                ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
        }
    }

    override fun onPause() {
        packageReceiver?.let {
            runCatching { unregisterReceiver(it) }
            packageReceiver = null
        }
        super.onPause()
    }
}

@Composable
private fun LauncherRoot(
    activity: MainActivity,
    preferences: LauncherPreferences,
    appsRepository: InstalledAppsRepository,
    packageEpoch: Int,
) {
    val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = LauncherSettings())
    var apps by androidx.compose.runtime.remember { mutableStateOf<List<LauncherApp>>(emptyList()) }
    var setupRequested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(packageEpoch) {
        apps = appsRepository.loadApps()
        if (apps.isNotEmpty() && !settings.favouritesInitialized) {
            preferences.initializeFavouritesIfNeeded(apps.map { it.packageName })
        }
    }

    val media by preferences.mediaHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    if (!settings.setupComplete || setupRequested) {
        SetupScreen(
            context = activity,
            preferences = preferences,
            onDone = { setupRequested = false },
        )
    } else {
        HomeScreen(
            activity = activity,
            preferences = preferences,
            appsRepository = appsRepository,
            settings = settings,
            apps = apps,
            mediaItems = media,
            onRerunSetup = { setupRequested = true },
        )
    }
}
