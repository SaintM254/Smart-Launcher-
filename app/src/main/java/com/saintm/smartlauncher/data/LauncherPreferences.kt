package com.saintm.smartlauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val Context.settingsStore by preferencesDataStore(name = "smart_launcher_settings")

/** User-controlled launcher settings. All values have safe defaults so a corrupt or partial file is harmless. */
data class LauncherSettings(
    val setupComplete: Boolean = false,
    val safeMode: Boolean = false,
    val homeOverrideEnabled: Boolean = true,
    val favourites: List<String> = emptyList(),
    val favouritesInitialized: Boolean = false,
    val showWatching: Boolean = true,
    val favouriteCount: Int = 5,
    val dockCapacity: Int = 8,
    val iconShape: String = "rounded",
    val cornerRoundness: Float = 0.28f,
    val iconSize: Int = 76,
    val showAppLabels: Boolean = true,
    val hiddenPackages: Set<String> = emptySet(),
    val renamedApps: Map<String, String> = emptyMap(),
    val customIcons: Map<String, String> = emptyMap(),
    val wallpaperId: String = "midnight",
    val customWallpaperUri: String? = null,
    val fontFamily: String = "system",
    val reduceAnimations: Boolean = false,
    val recentAppsEnabled: Boolean = false,
    val recentPackages: List<String> = emptyList(),
    val mediaFilterEnabled: Boolean = false,
    val allowedMediaPackages: Set<String> = emptySet(),
    val lastFocusedKey: String = "favorite:first",
)

class LauncherPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.settingsStore

    val settings: Flow<LauncherSettings> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map(::decodePreferences)

    suspend fun snapshot(): LauncherSettings = settings.first()

    suspend fun setSetupComplete(value: Boolean) = store.edit { it[K_SETUP] = value }
    suspend fun setSafeMode(value: Boolean) = store.edit { it[K_SAFE_MODE] = value }
    suspend fun setHomeOverride(value: Boolean) = store.edit { it[K_HOME_OVERRIDE] = value }
    suspend fun setShowWatching(value: Boolean) = store.edit { it[K_SHOW_WATCHING] = value }
    suspend fun setFavouriteCount(value: Int) = store.edit { it[K_FAVOURITE_COUNT] = value.coerceIn(3, 10) }
    suspend fun setDockCapacity(value: Int) = store.edit { it[K_DOCK_CAPACITY] = value.coerceIn(5, 14) }
    suspend fun setIconShape(value: String) = store.edit { it[K_ICON_SHAPE] = value.safeIconShape() }
    suspend fun setCornerRoundness(value: Float) = store.edit { it[K_CORNER_ROUNDNESS] = value.coerceIn(0f, 0.5f) }
    suspend fun setIconSize(value: Int) = store.edit { it[K_ICON_SIZE] = value.coerceIn(52, 112) }
    suspend fun setShowAppLabels(value: Boolean) = store.edit { it[K_SHOW_LABELS] = value }
    suspend fun setWallpaper(id: String, uri: String? = null) = store.edit {
        it[K_WALLPAPER] = id
        if (uri == null) it.remove(K_CUSTOM_WALLPAPER) else it[K_CUSTOM_WALLPAPER] = uri
    }
    suspend fun setFontFamily(value: String) = store.edit { it[K_FONT] = value.safeFontKey() }
    suspend fun setReduceAnimations(value: Boolean) = store.edit { it[K_REDUCE_ANIMATIONS] = value }
    suspend fun setRecentAppsEnabled(value: Boolean) = store.edit { it[K_RECENTS_ENABLED] = value }
    suspend fun setMediaFilter(enabled: Boolean, packages: Set<String>) = store.edit {
        it[K_MEDIA_FILTER_ENABLED] = enabled
        it[K_ALLOWED_MEDIA] = packages
    }
    suspend fun setLastFocusedKey(value: String) = store.edit { it[K_LAST_FOCUSED] = value.take(160) }

    suspend fun initializeFavouritesIfNeeded(packages: List<String>) = store.edit { prefs ->
        if (prefs[K_FAVOURITES_INITIALIZED] != true) {
            prefs[K_FAVOURITES] = encodeStringList(packages.distinct().take(5))
            prefs[K_FAVOURITES_INITIALIZED] = true
        }
    }

    suspend fun setFavourites(packages: List<String>) = store.edit {
        it[K_FAVOURITES] = encodeStringList(packages.distinct().take(60))
        it[K_FAVOURITES_INITIALIZED] = true
    }

    suspend fun setHiddenPackages(packages: Set<String>) = store.edit { it[K_HIDDEN_PACKAGES] = packages }

    suspend fun setAppName(packageName: String, name: String?) = store.edit { prefs ->
        val key = stringPreferencesKey("rename.$packageName")
        if (name.isNullOrBlank()) prefs.remove(key) else prefs[key] = name.trim().take(48)
    }

    suspend fun setCustomIcon(packageName: String, uri: String?) = store.edit { prefs ->
        val key = stringPreferencesKey("custom_icon.$packageName")
        if (uri.isNullOrBlank()) prefs.remove(key) else prefs[key] = uri
    }

    suspend fun addRecentPackage(packageName: String) = store.edit { prefs ->
        val updated = decodeStringList(prefs[K_RECENTS]).filterNot { it == packageName }
        prefs[K_RECENTS] = encodeStringList((listOf(packageName) + updated).take(8))
    }

    suspend fun recordMediaItem(item: MediaHistoryItem) = store.edit { prefs ->
        val items = decodeMediaItems(prefs[K_MEDIA_HISTORY]).filterNot { it.packageName == item.packageName }
        prefs[K_MEDIA_HISTORY] = encodeMediaItems((listOf(item) + items).take(12))
    }

    fun mediaHistory(): Flow<List<MediaHistoryItem>> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { decodeMediaItems(it[K_MEDIA_HISTORY]) }

    suspend fun clearMediaHistory() {
        store.edit { it.remove(K_MEDIA_HISTORY) }
        withContext(Dispatchers.IO) { File(appContext.filesDir, "watch-artwork").deleteRecursively() }
    }

    suspend fun reset() {
        store.edit { it.clear() }
        withContext(Dispatchers.IO) { File(appContext.filesDir, "watch-artwork").deleteRecursively() }
    }

    suspend fun exportJson(): String {
        val current = settings.first()
        return JSONObject().apply {
            put("formatVersion", 1)
            put("app", "Smart Launcher")
            put("setupComplete", current.setupComplete)
            put("safeMode", current.safeMode)
            put("homeOverrideEnabled", current.homeOverrideEnabled)
            put("favourites", current.favourites.toJsonArray())
            put("showWatching", current.showWatching)
            put("favouriteCount", current.favouriteCount)
            put("dockCapacity", current.dockCapacity)
            put("iconShape", current.iconShape)
            put("cornerRoundness", current.cornerRoundness.toDouble())
            put("iconSize", current.iconSize)
            put("showAppLabels", current.showAppLabels)
            put("hiddenPackages", current.hiddenPackages.sorted().toJsonArray())
            put("renamedApps", JSONObject(current.renamedApps))
            put("customIcons", JSONObject(current.customIcons))
            put("wallpaperId", current.wallpaperId)
            put("customWallpaperUri", current.customWallpaperUri)
            put("fontFamily", current.fontFamily)
            put("reduceAnimations", current.reduceAnimations)
            put("recentAppsEnabled", current.recentAppsEnabled)
            put("mediaFilterEnabled", current.mediaFilterEnabled)
            put("allowedMediaPackages", current.allowedMediaPackages.sorted().toJsonArray())
        }.toString(2)
    }

    /** Import is deliberately strict about the format version and conservative about every value. */
    suspend fun importJson(json: String): String? {
        if (json.length > 200_000) return "That settings file is too large. Your settings were not changed."
        val parsed = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return "That file is not valid JSON. Your settings were not changed."
        }
        if (parsed.optInt("formatVersion", -1) != 1) {
            return "This settings file uses an unsupported version. Your settings were not changed."
        }
        val safeWallpaper = parsed.optString("wallpaperId", "midnight").takeIf { it in WALLPAPER_IDS } ?: "midnight"
        val safeUri = parsed.optString("customWallpaperUri", "").takeIf { it.startsWith("content://") }
        return try {
            store.edit { prefs ->
                prefs[K_SETUP] = parsed.optBoolean("setupComplete", false)
                prefs[K_SAFE_MODE] = parsed.optBoolean("safeMode", false)
                prefs[K_HOME_OVERRIDE] = parsed.optBoolean("homeOverrideEnabled", true)
                prefs[K_FAVOURITES] = encodeStringList(parsed.optJSONArray("favourites").toStringList().take(60))
                prefs[K_FAVOURITES_INITIALIZED] = true
                prefs[K_SHOW_WATCHING] = parsed.optBoolean("showWatching", true)
                prefs[K_FAVOURITE_COUNT] = parsed.optInt("favouriteCount", 5).coerceIn(3, 10)
                prefs[K_DOCK_CAPACITY] = parsed.optInt("dockCapacity", 8).coerceIn(5, 14)
                prefs[K_ICON_SHAPE] = parsed.optString("iconShape", "rounded").safeIconShape()
                prefs[K_CORNER_ROUNDNESS] = parsed.optDouble("cornerRoundness", 0.28).toFloat().coerceIn(0f, 0.5f)
                prefs[K_ICON_SIZE] = parsed.optInt("iconSize", 76).coerceIn(52, 112)
                prefs[K_SHOW_LABELS] = parsed.optBoolean("showAppLabels", true)
                prefs[K_HIDDEN_PACKAGES] = parsed.optJSONArray("hiddenPackages").toStringList().toSet()
                prefs[K_WALLPAPER] = safeWallpaper
                if (safeUri == null) prefs.remove(K_CUSTOM_WALLPAPER) else prefs[K_CUSTOM_WALLPAPER] = safeUri
                prefs[K_FONT] = parsed.optString("fontFamily", "system").safeFontKey()
                prefs[K_REDUCE_ANIMATIONS] = parsed.optBoolean("reduceAnimations", false)
                prefs[K_RECENTS_ENABLED] = parsed.optBoolean("recentAppsEnabled", false)
                prefs[K_MEDIA_FILTER_ENABLED] = parsed.optBoolean("mediaFilterEnabled", false)
                prefs[K_ALLOWED_MEDIA] = parsed.optJSONArray("allowedMediaPackages").toStringList().toSet()
                prefs.asMap().keys.filter { it.name.startsWith("rename.") || it.name.startsWith("custom_icon.") }
                    .forEach { prefs.remove(stringPreferencesKey(it.name)) }
                copyStringMap(parsed.optJSONObject("renamedApps"), "rename.", prefs)
                copyStringMap(parsed.optJSONObject("customIcons"), "custom_icon.", prefs)
            }
            null
        } catch (_: Exception) {
            "The settings could not be imported. Your existing settings were kept."
        }
    }

    private fun decodePreferences(prefs: Preferences): LauncherSettings {
        val values = prefs.asMap()
        val renames = values.entries.mapNotNull { (key, value) ->
            if (key.name.startsWith("rename.")) (value as? String)?.let { key.name.removePrefix("rename.") to it } else null
        }.toMap()
        val customIcons = values.entries.mapNotNull { (key, value) ->
            if (key.name.startsWith("custom_icon.")) (value as? String)?.let { key.name.removePrefix("custom_icon.") to it } else null
        }.toMap()
        return LauncherSettings(
            setupComplete = prefs[K_SETUP] ?: false,
            safeMode = prefs[K_SAFE_MODE] ?: false,
            homeOverrideEnabled = prefs[K_HOME_OVERRIDE] ?: true,
            favourites = decodeStringList(prefs[K_FAVOURITES]),
            favouritesInitialized = prefs[K_FAVOURITES_INITIALIZED] ?: false,
            showWatching = prefs[K_SHOW_WATCHING] ?: true,
            favouriteCount = (prefs[K_FAVOURITE_COUNT] ?: 5).coerceIn(3, 10),
            dockCapacity = (prefs[K_DOCK_CAPACITY] ?: 8).coerceIn(5, 14),
            iconShape = (prefs[K_ICON_SHAPE] ?: "rounded").safeIconShape(),
            cornerRoundness = (prefs[K_CORNER_ROUNDNESS] ?: 0.28f).coerceIn(0f, 0.5f),
            iconSize = (prefs[K_ICON_SIZE] ?: 76).coerceIn(52, 112),
            showAppLabels = prefs[K_SHOW_LABELS] ?: true,
            hiddenPackages = prefs[K_HIDDEN_PACKAGES] ?: emptySet(),
            renamedApps = renames,
            customIcons = customIcons,
            wallpaperId = (prefs[K_WALLPAPER] ?: "midnight").takeIf { it in WALLPAPER_IDS } ?: "midnight",
            customWallpaperUri = prefs[K_CUSTOM_WALLPAPER],
            fontFamily = (prefs[K_FONT] ?: "system").safeFontKey(),
            reduceAnimations = prefs[K_REDUCE_ANIMATIONS] ?: false,
            recentAppsEnabled = prefs[K_RECENTS_ENABLED] ?: false,
            recentPackages = decodeStringList(prefs[K_RECENTS]),
            mediaFilterEnabled = prefs[K_MEDIA_FILTER_ENABLED] ?: false,
            allowedMediaPackages = prefs[K_ALLOWED_MEDIA] ?: emptySet(),
            lastFocusedKey = prefs[K_LAST_FOCUSED] ?: "favorite:first",
        )
    }

    private fun copyStringMap(source: JSONObject?, prefix: String, prefs: androidx.datastore.preferences.core.MutablePreferences) {
        source?.keys()?.forEach { key ->
            val value = source.optString(key).trim()
            if (key.length in 1..200 && value.isNotEmpty()) {
                prefs[stringPreferencesKey(prefix + key)] = value.take(500)
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        if (this@toStringList == null) return@buildList
        for (index in 0 until length().coerceAtMost(100)) {
            val item = optString(index).trim()
            if (item.isNotEmpty() && item.length <= 200) add(item)
        }
    }

    private fun List<String>.toJsonArray() = JSONArray().apply { forEach(::put) }

    private fun encodeStringList(values: List<String>): String = JSONArray(values).toString()

    private fun decodeStringList(raw: String?): List<String> = try {
        JSONArray(raw ?: "[]").toStringList()
    } catch (_: JSONException) {
        emptyList()
    }

    private fun encodeMediaItems(items: List<MediaHistoryItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("packageName", item.packageName)
                put("appName", item.appName)
                put("title", item.title)
                put("subtitle", item.subtitle)
                put("artworkPath", item.artworkPath)
                put("updatedAt", item.updatedAt)
            })
        }
    }.toString()

    private fun decodeMediaItems(raw: String?): List<MediaHistoryItem> = try {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length().coerceAtMost(12)) {
                val obj = array.optJSONObject(index) ?: continue
                val pkg = obj.optString("packageName").takeIf(String::isNotBlank) ?: continue
                val title = obj.optString("title").takeIf(String::isNotBlank) ?: continue
                add(
                    MediaHistoryItem(
                        packageName = pkg,
                        appName = obj.optString("appName", pkg),
                        title = title,
                        subtitle = obj.optString("subtitle").takeIf(String::isNotBlank),
                        artworkPath = obj.optString("artworkPath").takeIf(String::isNotBlank),
                        updatedAt = obj.optLong("updatedAt"),
                    ),
                )
            }
        }
    } catch (_: JSONException) {
        emptyList()
    }

    companion object {
        private val K_SETUP = booleanPreferencesKey("setup_complete")
        private val K_SAFE_MODE = booleanPreferencesKey("safe_mode")
        private val K_HOME_OVERRIDE = booleanPreferencesKey("home_override")
        private val K_FAVOURITES = stringPreferencesKey("favourites")
        private val K_FAVOURITES_INITIALIZED = booleanPreferencesKey("favourites_initialized")
        private val K_SHOW_WATCHING = booleanPreferencesKey("show_watching")
        private val K_FAVOURITE_COUNT = intPreferencesKey("favourite_count")
        private val K_DOCK_CAPACITY = intPreferencesKey("dock_capacity")
        private val K_ICON_SHAPE = stringPreferencesKey("icon_shape")
        private val K_CORNER_ROUNDNESS = floatPreferencesKey("corner_roundness")
        private val K_ICON_SIZE = intPreferencesKey("icon_size")
        private val K_SHOW_LABELS = booleanPreferencesKey("show_labels")
        private val K_HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
        private val K_WALLPAPER = stringPreferencesKey("wallpaper_id")
        private val K_CUSTOM_WALLPAPER = stringPreferencesKey("custom_wallpaper")
        private val K_FONT = stringPreferencesKey("font_family")
        private val K_REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        private val K_RECENTS_ENABLED = booleanPreferencesKey("recent_apps_enabled")
        private val K_RECENTS = stringPreferencesKey("recent_packages")
        private val K_MEDIA_FILTER_ENABLED = booleanPreferencesKey("media_filter_enabled")
        private val K_ALLOWED_MEDIA = stringSetPreferencesKey("allowed_media_packages")
        private val K_LAST_FOCUSED = stringPreferencesKey("last_focused")
    }
}

data class MediaHistoryItem(
    val packageName: String,
    val appName: String,
    val title: String,
    val subtitle: String? = null,
    val artworkPath: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

private val WALLPAPER_IDS = setOf(
    "midnight", "lagoon", "ember", "dawn", "forest", "violet", "sand", "glacier", "orbit",
)

private fun String.safeIconShape(): String = when (this) {
    "square", "rounded", "more-rounded", "circle" -> this
    else -> "rounded"
}

private fun String.safeFontKey(): String = when (this) {
    "system", "inter", "poppins", "montserrat", "playfair", "lora" -> this
    else -> "system"
}
