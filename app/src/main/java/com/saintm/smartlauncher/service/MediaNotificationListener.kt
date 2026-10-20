package com.saintm.smartlauncher.service

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.saintm.smartlauncher.data.LauncherPreferences
import com.saintm.smartlauncher.data.MediaHistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads media-session metadata only. Android does not expose third-party apps' official
 * "Watch Next" catalogs to other launchers; observing MediaSession is the practical alternative.
 */
class MediaNotificationListener : NotificationListenerService() {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences by lazy { LauncherPreferences(this) }
    private val callbacks = mutableMapOf<String, MediaController.Callback>()
    private val controllers = mutableMapOf<String, MediaController>()
    private val savedFingerprints = ConcurrentHashMap<String, String>()
    private var settingsWatcher: Job? = null
    private var sessions: MediaSessionManager? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessions = getSystemService(MediaSessionManager::class.java)
        settingsWatcher?.cancel()
        settingsWatcher = work.launch {
            preferences.settings
                .map { it.mediaFilterEnabled to it.allowedMediaPackages }
                .distinctUntilChanged()
                .collect { refreshSessions() }
        }
        refreshSessions()
    }

    // Notification contents are never inspected. These callbacks only prompt a fresh session query.
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refreshSessions()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refreshSessions()

    override fun onListenerDisconnected() {
        settingsWatcher?.cancel()
        settingsWatcher = null
        callbacks.forEach { (packageName, callback) ->
            runCatching { controllers[packageName]?.unregisterCallback(callback) }
        }
        callbacks.clear()
        controllers.clear()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        callbacks.forEach { (packageName, callback) ->
            runCatching { controllers[packageName]?.unregisterCallback(callback) }
        }
        callbacks.clear()
        controllers.clear()
        work.cancel()
        super.onDestroy()
    }

    private fun refreshSessions() {
        val active = runCatching { sessions?.getActiveSessions(listenerComponent()).orEmpty() }.getOrDefault(emptyList())
        val activePackages = active.mapTo(mutableSetOf()) { it.packageName }
        callbacks.keys.filterNot(activePackages::contains).toList().forEach { packageName ->
            callbacks.remove(packageName)?.let { callback -> runCatching { controllers[packageName]?.unregisterCallback(callback) } }
            controllers.remove(packageName)
        }
        active.forEach { controller ->
            val packageName = controller.packageName
            if (controllers[packageName] !== controller) {
                callbacks.remove(packageName)?.let { callback -> runCatching { controllers[packageName]?.unregisterCallback(callback) } }
                controllers[packageName] = controller
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = capture(controller)
                    override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = capture(controller)
                    override fun onSessionDestroyed() {
                        callbacks.remove(packageName)?.let { old -> runCatching { controller.unregisterCallback(old) } }
                        controllers.remove(packageName)
                        refreshSessions()
                    }
                }
                callbacks[packageName] = callback
                runCatching { controller.registerCallback(callback) }
            }
            capture(controller)
        }
    }

    private fun capture(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return
        if (title.isBlank()) return
        val state = controller.playbackState?.state
        if (state == android.media.session.PlaybackState.STATE_NONE) return

        val fingerprint = "$title|${metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()}"
        if (savedFingerprints[controller.packageName] == fingerprint) return

        work.launch {
            val settings = preferences.settings.first()
            if (settings.mediaFilterEnabled && controller.packageName !in settings.allowedMediaPackages) {
                savedFingerprints.remove(controller.packageName)
                return@launch
            }
            savedFingerprints[controller.packageName] = fingerprint

            val appName = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(controller.packageName, 0)).toString()
            }.getOrDefault(controller.packageName)
            val subtitle = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val artworkPath = art?.let { saveArtwork(controller.packageName, it) }
            preferences.recordMediaItem(
                MediaHistoryItem(
                    packageName = controller.packageName,
                    appName = appName,
                    title = title.trim().take(160),
                    subtitle = subtitle?.trim()?.take(120),
                    artworkPath = artworkPath,
                ),
            )
        }
    }

    private suspend fun saveArtwork(packageName: String, source: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(filesDir, "watch-artwork").apply { mkdirs() }
            val file = File(directory, "${packageName.hashCode().toUInt().toString(16)}.jpg")
            val maxSide = 480
            val factor = minOf(1f, maxSide.toFloat() / maxOf(source.width, source.height))
            val width = (source.width * factor).toInt().coerceAtLeast(1)
            val height = (source.height * factor).toInt().coerceAtLeast(1)
            val scaled = if (width == source.width && height == source.height) source
            else Bitmap.createScaledBitmap(source, width, height, true)
            FileOutputStream(file).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
            }
            if (scaled !== source) scaled.recycle()
            file.absolutePath
        }.getOrNull()
    }

    private fun listenerComponent() = ComponentName(this, MediaNotificationListener::class.java)
}
