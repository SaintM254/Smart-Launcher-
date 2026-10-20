package com.saintm.smartlauncher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saintm.smartlauncher.data.InstalledAppsRepository
import com.saintm.smartlauncher.data.moveFavourite
import com.saintm.smartlauncher.data.visibleMediaItems
import com.saintm.smartlauncher.data.LauncherApp
import com.saintm.smartlauncher.data.LauncherPreferences
import com.saintm.smartlauncher.data.LauncherSettings
import com.saintm.smartlauncher.data.MediaHistoryItem
import com.saintm.smartlauncher.util.WallpaperAssets
import com.saintm.smartlauncher.util.WallpaperFactory
import com.saintm.smartlauncher.util.canOpenAssistant
import com.saintm.smartlauncher.util.isDefaultHome
import com.saintm.smartlauncher.util.isHomeKeyServiceEnabled
import com.saintm.smartlauncher.util.isMediaListenerEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private data class ConnectionStatus(val kind: String, val connected: Boolean, val validated: Boolean)
private data class FavoriteTarget(val packageName: String)
private data class MenuAction(val title: String, val subtitle: String? = null, val action: () -> Unit)

@Composable
fun HomeScreen(
    activity: MainActivity,
    preferences: LauncherPreferences,
    appsRepository: InstalledAppsRepository,
    settings: LauncherSettings,
    apps: List<LauncherApp>,
    mediaItems: List<MediaHistoryItem>,
    onRerunSetup: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val requestedWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtLeast(320)
    val requestedHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }.coerceAtLeast(180)
    var wallpaperAssets by remember { mutableStateOf<WallpaperAssets?>(null) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var panelOpen by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var favoriteTarget by remember { mutableStateOf<FavoriteTarget?>(null) }
    var dockTarget by remember { mutableStateOf<LauncherApp?>(null) }
    var permissionsEpoch by remember { mutableLongStateOf(0L) }
    var pendingCustomIconPackage by remember { mutableStateOf<String?>(null) }
    var backDownAt by remember { mutableLongStateOf(0L) }
    var backIsDown by remember { mutableStateOf(false) }
    var displayedArtwork by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var customIconBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    LaunchedEffect(settings.wallpaperId, settings.customWallpaperUri, requestedWidth, requestedHeight) {
        wallpaperAssets = runCatching {
            WallpaperFactory.load(activity, settings.wallpaperId, settings.customWallpaperUri, requestedWidth, requestedHeight)
        }.getOrNull()
    }

    val filteredMedia = visibleMediaItems(mediaItems, settings.mediaFilterEnabled, settings.allowedMediaPackages)
    val shownMedia = List(3) { filteredMedia.getOrNull(it) }
    LaunchedEffect(shownMedia.map { it?.artworkPath }) {
        displayedArtwork = withContext(Dispatchers.IO) {
            shownMedia.mapNotNull { item ->
                val path = item?.artworkPath ?: return@mapNotNull null
                val bitmap = readArtwork(path) ?: return@mapNotNull null
                path to bitmap
            }.toMap()
        }
    }

    LaunchedEffect(settings.customIcons) {
        customIconBitmaps = buildMap {
            settings.customIcons.entries.take(30).forEach { (packageName, uri) ->
                appsRepository.loadCustomIcon(uri)?.let { put(packageName, it) }
            }
        }
    }

    val packageNames = remember(apps) { apps.map { it.packageName }.toSet() }
    LaunchedEffect(packageNames, settings.favouritesInitialized) {
        if (settings.favouritesInitialized && packageNames.isNotEmpty()) {
            val clean = settings.favourites.filter { it in packageNames }
            if (clean != settings.favourites) preferences.setFavourites(clean)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val homeServiceEnabled = remember(permissionsEpoch) { isHomeKeyServiceEnabled(activity) }
    val notificationAccessEnabled = remember(permissionsEpoch) { isMediaListenerEnabled(activity) }
    val defaultHome = remember(permissionsEpoch) { isDefaultHome(activity) }
    val network = rememberConnectionStatus(activity)
    val assistantAvailable = remember(permissionsEpoch) { canOpenAssistant(activity) }
    val time by androidx.compose.runtime.produceState(initialValue = formattedTime()) {
        while (true) {
            value = formattedTime()
            delay(30_000L)
        }
    }

    val packageMap = remember(apps) { apps.associateBy { it.packageName } }
    val visibleDockApps = remember(apps, settings.hiddenPackages) { apps.filterNot { it.packageName in settings.hiddenPackages } }
    val visibleFavourites = settings.favourites
        .take(settings.favouriteCount)
        .mapNotNull(packageMap::get)

    val focusRequesters = remember(apps.map { it.packageName }, assistantAvailable) {
        buildMap {
            put("settings", FocusRequester())
            put("voice", FocusRequester())
            repeat(3) { put("watch:$it", FocusRequester()) }
            put("favorite:add", FocusRequester())
            apps.forEach { app ->
                put("favorite:${app.packageName}", FocusRequester())
                put("dock:${app.packageName}", FocusRequester())
                put("recent:${app.packageName}", FocusRequester())
            }
        }
    }

    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch { preferences.setWallpaper("midnight", uri.toString()) }
        }
    }
    val customIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val packageName = pendingCustomIconPackage
        pendingCustomIconPackage = null
        if (uri != null && packageName != null) {
            runCatching { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch { preferences.setCustomIcon(packageName, uri.toString()) }
        }
    }
    val exportSettings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                val json = preferences.exportJson()
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("The file could not be opened")
                }
            }
            showMessage(activity, if (result.isSuccess) "Settings exported." else "Could not save that settings file.")
        }
    }
    val importSettings = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(100_000) }
                        ?: error("The file could not be opened")
                }
            }
            if (result.isFailure) showMessage(activity, "Could not read that file. Your settings were not changed.")
            else {
                val importError = preferences.importJson(result.getOrThrow())
                showMessage(activity, importError ?: "Settings imported.")
            }
        }
    }

    fun message(text: String) = showMessage(activity, text)
    fun recordFocus(key: String): Modifier = Modifier
        .onFocusChanged { state -> if (state.isFocused) scope.launch { preferences.setLastFocusedKey(key) } }
    fun openApp(packageName: String) {
        scope.launch { preferences.addRecentPackage(packageName) }
        if (!appsRepository.launch(packageName)) message("This app does not currently provide a launchable activity.")
    }
    fun addFavourite(packageName: String) {
        if (packageName in settings.favourites) return
        val updated = settings.favourites + packageName
        scope.launch {
            preferences.setFavourites(updated)
            if (updated.size > settings.favouriteCount && settings.favouriteCount < 10) {
                preferences.setFavouriteCount(updated.size.coerceAtMost(10))
            }
        }
    }

    BackHandler(enabled = true) {
        if (panelOpen) panelOpen = false
        // Back on the launcher intentionally does not leave for another Home app.
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { rootCoordinates = it }
            .onPreviewKeyEvent { event ->
                val key = event.nativeKeyEvent
                val isBack = key.keyCode == AndroidKeyEvent.KEYCODE_BACK || key.keyCode == AndroidKeyEvent.KEYCODE_ESCAPE
                if (event.type == KeyEventType.KeyDown && (key.keyCode == AndroidKeyEvent.KEYCODE_MENU || key.keyCode == AndroidKeyEvent.KEYCODE_SETTINGS)) {
                    panelOpen = true
                    true
                } else if (!panelOpen && isBack) {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (key.repeatCount == 0) {
                                val pressedAt = SystemClock.elapsedRealtime()
                                backDownAt = pressedAt
                                backIsDown = true
                                scope.launch {
                                    delay(5_000L)
                                    if (backIsDown && backDownAt == pressedAt) {
                                        backIsDown = false
                                        preferences.setSafeMode(true)
                                        message("Safe mode is on. The Home key override is paused.")
                                    }
                                }
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            backIsDown = false
                            true
                        }
                        else -> true
                    }
                } else {
                    false
                }
            },
    ) {
        val lightWallpaper = remember(wallpaperAssets?.original) { wallpaperAssets?.original?.let(::isLightBitmap) ?: false }
        val contentColor = if (lightWallpaper) Color(0xFF14233A) else Color.White
        val glassTint = if (lightWallpaper) Color.Black.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.13f)

        Crossfade(
            targetState = wallpaperAssets,
            animationSpec = tween(if (settings.reduceAnimations) 0 else 220),
            label = "launcher-wallpaper",
        ) { assets ->
            if (assets != null) {
                Image(bitmap = assets.original.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF06152D), Color(0xFF17466A), Color(0xFF08152D)))))
            }
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .focusProperties { canFocus = !panelOpen }
                .padding(horizontal = (maxWidth * 0.045f).coerceIn(24.dp, 54.dp), vertical = (maxHeight * 0.045f).coerceIn(14.dp, 32.dp)),
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                HomeTopBar(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    time = time,
                    network = network,
                    settings = settings,
                    wallpaper = wallpaperAssets?.original,
                    glassBitmap = wallpaperAssets?.glass,
                    rootCoordinates = rootCoordinates,
                    contentColor = contentColor,
                    glassTint = glassTint,
                    assistantAvailable = assistantAvailable,
                    settingsRequester = focusRequesters.getValue("settings"),
                    voiceRequester = focusRequesters.getValue("voice"),
                    onFocusSettings = recordFocus("settings"),
                    onFocusVoice = recordFocus("voice"),
                    onOpenSettings = { panelOpen = true },
                    onVoiceSearch = {
                        runCatching { activity.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { message("No voice assistant is available on this device.") }
                    },
                )

                PermissionReminders(
                    homeServiceEnabled = homeServiceEnabled,
                    notificationAccessEnabled = notificationAccessEnabled,
                    isDefaultHome = defaultHome,
                    safeMode = settings.safeMode,
                    reduceAnimations = settings.reduceAnimations,
                    contentColor = contentColor,
                    onSetup = onRerunSetup,
                    onOpenNotificationSettings = {
                        runCatching { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    },
                    onOpenSettings = { panelOpen = true },
                )

                if (settings.showWatching) {
                    Column(Modifier.weight(if (settings.recentAppsEnabled) 0.88f else 1f).fillMaxWidth()) {
                        SectionHeading("Continue watching", contentColor, settings.fontFamily)
                        Row(
                            Modifier.weight(1f).fillMaxWidth().focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            repeat(3) { index ->
                                val item = shownMedia[index]
                                val path = item?.artworkPath
                                WatchCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    item = item,
                                    artwork = path?.let(displayedArtwork::get),
                                    missingPermission = !notificationAccessEnabled,
                                    wallpaper = wallpaperAssets?.original,
                                    glassBitmap = wallpaperAssets?.glass,
                                    rootCoordinates = rootCoordinates,
                                    glassTint = glassTint,
                                    reduceAnimations = settings.reduceAnimations,
                                    focusRequester = focusRequesters.getValue("watch:$index"),
                                    onFocus = recordFocus("watch:$index"),
                                    onClick = {
                                        if (!notificationAccessEnabled) {
                                            runCatching { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                                        } else if (item != null) openApp(item.packageName)
                                        else message("Play a video or music app and its latest session will show here.")
                                    },
                                )
                            }
                        }
                    }
                }

                Column(Modifier.weight(0.72f).fillMaxWidth()) {
                    SectionHeading("Favourites", contentColor, settings.fontFamily)
                    FrostedSurface(
                        modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 62.dp),
                        shape = RoundedCornerShape(24.dp),
                        wallpaper = wallpaperAssets?.original,
                        glassBitmap = wallpaperAssets?.glass,
                        rootCoordinates = rootCoordinates,
                        tint = glassTint,
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(7.dp).focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            visibleFavourites.forEach { app ->
                                HomeAppTile(
                                    displayName = settings.renamedApps[app.packageName] ?: app.label,
                                    icon = customIconBitmaps[app.packageName] ?: app.icon,
                                    modifier = Modifier.weight(1f).fillMaxHeight().then(focusRequesters.getValue("favorite:${app.packageName}").let { Modifier.focusRequester(it) }).then(recordFocus("favorite:${app.packageName}")),
                                    iconSize = (settings.iconSize * 0.72f).dp,
                                    settings = settings,
                                    reduceAnimations = settings.reduceAnimations,
                                    contentColor = contentColor,
                                    onClick = { openApp(app.packageName) },
                                    onLongClick = { favoriteTarget = FavoriteTarget(app.packageName) },
                                    shape = iconShape(settings),
                                )
                            }
                            FocusSurface(
                                onClick = { showPicker = true },
                                modifier = Modifier.weight(0.62f).fillMaxHeight()
                                    .focusRequester(focusRequesters.getValue("favorite:add"))
                                    .then(recordFocus("favorite:add")),
                                shape = RoundedCornerShape(18.dp),
                                reduceAnimations = settings.reduceAnimations,
                                baseColor = Color.White.copy(alpha = 0.04f),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    androidx.tv.material3.Text("+", color = contentColor, fontSize = 32.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light)
                                    androidx.tv.material3.Text("Add app", color = contentColor.copy(alpha = .72f), fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                if (settings.recentAppsEnabled) {
                    Column(Modifier.weight(0.36f).fillMaxWidth()) {
                        SectionHeading("Recent", contentColor, settings.fontFamily)
                        Row(Modifier.weight(1f).fillMaxWidth().focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val recentApps = settings.recentPackages.take(5).mapNotNull(packageMap::get)
                            if (recentApps.isEmpty()) {
                                androidx.tv.material3.Text("Apps you open will appear here.", color = contentColor.copy(alpha = .7f), fontSize = 12.sp)
                            } else recentApps.forEach { app ->
                                RecentAppTile(
                                    displayName = settings.renamedApps[app.packageName] ?: app.label,
                                    icon = customIconBitmaps[app.packageName] ?: app.icon,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                        .focusRequester(focusRequesters.getValue("recent:${app.packageName}"))
                                        .then(recordFocus("recent:${app.packageName}")),
                                    reduceAnimations = settings.reduceAnimations,
                                    contentColor = contentColor,
                                    onClick = { openApp(app.packageName) },
                                )
                            }
                        }
                    }
                }

                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionHeading("Apps", contentColor, settings.fontFamily)
                        androidx.tv.material3.Text("A–Z", color = contentColor.copy(alpha = .58f), fontSize = 12.sp)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val itemWidth = (maxWidth / settings.dockCapacity.toFloat()).coerceIn(70.dp, 138.dp)
                        LazyRow(
                            modifier = Modifier.fillMaxSize().focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            items(visibleDockApps, key = { it.packageName }) { app ->
                                HomeAppTile(
                                    displayName = settings.renamedApps[app.packageName] ?: app.label,
                                    icon = customIconBitmaps[app.packageName] ?: app.icon,
                                    modifier = Modifier.width(itemWidth).fillMaxHeight()
                                        .focusRequester(focusRequesters.getValue("dock:${app.packageName}"))
                                        .then(recordFocus("dock:${app.packageName}")),
                                    iconSize = minOf(settings.iconSize.dp, itemWidth * 0.70f),
                                    settings = settings,
                                    reduceAnimations = settings.reduceAnimations,
                                    contentColor = contentColor,
                                    onClick = { openApp(app.packageName) },
                                    onLongClick = { dockTarget = app },
                                    shape = iconShape(settings),
                                )
                            }
                        }
                        if (visibleDockApps.isEmpty()) {
                            androidx.tv.material3.Text("No apps are available yet.", color = contentColor.copy(alpha = .76f), fontSize = 15.sp, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }

        SettingsPanel(
            visible = panelOpen,
            context = activity,
            preferences = preferences,
            settings = settings,
            apps = apps,
            wallpaper = wallpaperAssets?.original,
            glassBitmap = wallpaperAssets?.glass,
            rootCoordinates = rootCoordinates,
            onClose = { panelOpen = false },
            onChooseWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
            onChooseCustomIcon = { packageName ->
                pendingCustomIconPackage = packageName
                customIconPicker.launch(arrayOf("image/*"))
            },
            onExport = { exportSettings.launch("smart-launcher-settings.json") },
            onImport = { importSettings.launch(arrayOf("application/json", "text/json", "text/plain")) },
            onRerunSetup = onRerunSetup,
            onMessage = ::message,
        )

        if (showPicker) {
            AppPickerDialog(
                apps = apps,
                favourites = settings.favourites,
                renamedApps = settings.renamedApps,
                reduceAnimations = settings.reduceAnimations,
                onToggle = { packageName ->
                    val updated = if (packageName in settings.favourites) settings.favourites - packageName else settings.favourites + packageName
                    scope.launch {
                        preferences.setFavourites(updated)
                        if (updated.size > settings.favouriteCount && settings.favouriteCount < 10) {
                            preferences.setFavouriteCount(updated.size.coerceAtMost(10))
                        }
                    }
                },
                onDismiss = { showPicker = false },
            )
        }
        favoriteTarget?.let { target ->
            val index = settings.favourites.indexOf(target.packageName)
            val label = settings.renamedApps[target.packageName] ?: packageMap[target.packageName]?.label ?: target.packageName
            ActionListDialog(
                title = label,
                actions = listOf(
                    MenuAction("Remove from favourites", "This app remains in the A–Z dock") {
                        scope.launch { preferences.setFavourites(settings.favourites.filterNot { it == target.packageName }) }
                        favoriteTarget = null
                    },
                    MenuAction("Move left") {
                        val moved = moveFavourite(settings.favourites, target.packageName, -1)
                        if (moved != settings.favourites) scope.launch { preferences.setFavourites(moved) }
                        else message("This favourite is already first.")
                        favoriteTarget = null
                    },
                    MenuAction("Move right") {
                        val moved = moveFavourite(settings.favourites, target.packageName, 1)
                        if (moved != settings.favourites) scope.launch { preferences.setFavourites(moved) }
                        else message("This favourite is already last.")
                        favoriteTarget = null
                    },
                ),
                reduceAnimations = settings.reduceAnimations,
                onDismiss = { favoriteTarget = null },
            )
        }
        dockTarget?.let { app ->
            val isFavourite = app.packageName in settings.favourites
            val actions = buildList {
                add(
                    MenuAction(if (isFavourite) "Remove from favourites" else "Add to favourites") {
                        if (isFavourite) scope.launch { preferences.setFavourites(settings.favourites.filterNot { it == app.packageName }) }
                        else addFavourite(app.packageName)
                        dockTarget = null
                    },
                )
                add(MenuAction("App info") {
                    runCatching { activity.startActivity(appsRepository.appInfoIntent(app.packageName)) }
                        .onFailure { message("Android could not open app info.") }
                    dockTarget = null
                })
                add(MenuAction("Uninstall") {
                    runCatching { activity.startActivity(appsRepository.uninstallIntent(app.packageName)) }
                        .onFailure { message("Android did not allow this app to be removed.") }
                    dockTarget = null
                })
            }
            ActionListDialog(
                title = settings.renamedApps[app.packageName] ?: app.label,
                actions = actions,
                reduceAnimations = settings.reduceAnimations,
                onDismiss = { dockTarget = null },
            )
        }
    }

    LaunchedEffect(panelOpen, apps.size, settings.lastFocusedKey) {
        if (!panelOpen) {
            withFrameNanos { }
            delay(70)
            requestSavedFocus(settings.lastFocusedKey, focusRequesters, settings, visibleFavourites, visibleDockApps)
        }
    }
}

@Composable
private fun HomeTopBar(
    modifier: Modifier,
    time: String,
    network: ConnectionStatus,
    settings: LauncherSettings,
    wallpaper: Bitmap?,
    glassBitmap: Bitmap?,
    rootCoordinates: LayoutCoordinates?,
    contentColor: Color,
    glassTint: Color,
    assistantAvailable: Boolean,
    settingsRequester: FocusRequester,
    voiceRequester: FocusRequester,
    onFocusSettings: Modifier,
    onFocusVoice: Modifier,
    onOpenSettings: () -> Unit,
    onVoiceSearch: () -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.tv.material3.Text(
            "SMART LAUNCHER",
            color = contentColor.copy(alpha = .84f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.9.sp,
            fontFamily = fontFamilyFor(settings.fontFamily),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (assistantAvailable) {
                FocusSurface(
                    onClick = onVoiceSearch,
                    modifier = Modifier.size(44.dp).focusRequester(voiceRequester).then(onFocusVoice),
                    shape = CircleShape,
                    reduceAnimations = settings.reduceAnimations,
                    baseColor = Color.White.copy(alpha = .08f),
                ) { SimpleIcon("voice", Modifier.size(19.dp), color = contentColor) }
            }
            FrostedSurface(
                modifier = Modifier.height(46.dp),
                shape = CircleShape,
                wallpaper = wallpaper,
                glassBitmap = glassBitmap,
                rootCoordinates = rootCoordinates,
                tint = glassTint,
            ) {
                Row(
                    Modifier.padding(start = 17.dp, end = 7.dp).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.tv.material3.Text(time, color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = fontFamilyFor(settings.fontFamily))
                    SimpleIcon(network.iconKind(), Modifier.size(19.dp), color = contentColor)
                    androidx.tv.material3.Text(network.label(), color = contentColor.copy(alpha = .85f), fontSize = 11.sp, maxLines = 1)
                    FocusSurface(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(38.dp).focusRequester(settingsRequester).then(onFocusSettings),
                        shape = CircleShape,
                        reduceAnimations = settings.reduceAnimations,
                        baseColor = Color.White.copy(alpha = .08f),
                    ) { SimpleIcon("settings", Modifier.size(20.dp), color = contentColor) }
                }
            }
        }
    }
}

@Composable
private fun PermissionReminders(
    homeServiceEnabled: Boolean,
    notificationAccessEnabled: Boolean,
    isDefaultHome: Boolean,
    safeMode: Boolean,
    reduceAnimations: Boolean,
    contentColor: Color,
    onSetup: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val reminders = buildList {
        if (safeMode) add("Safe mode is on · Home override paused" to onOpenSettings)
        else if (!homeServiceEnabled) add("Enable the Home key shortcut" to onSetup)
        if (!isDefaultHome) add("Choose Smart Launcher as your Home app" to onSetup)
        if (!notificationAccessEnabled) add("Watching cards need notification access" to onOpenNotificationSettings)
    }.take(2)
    if (reminders.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().height(34.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            reminders.forEach { (text, action) ->
                FocusSurface(
                    onClick = action,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    reduceAnimations = reduceAnimations,
                    baseColor = Color(0xFF14243A).copy(alpha = .72f),
                ) {
                    androidx.tv.material3.Text(text, color = contentColor, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    } else {
        Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun SectionHeading(text: String, color: Color, fontKey: String) {
    androidx.tv.material3.Text(
        text,
        color = color.copy(alpha = .86f),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = fontFamilyFor(fontKey),
        modifier = Modifier.height(20.dp),
    )
}

@Composable
private fun WatchCard(
    modifier: Modifier,
    item: MediaHistoryItem?,
    artwork: Bitmap?,
    missingPermission: Boolean,
    wallpaper: Bitmap?,
    glassBitmap: Bitmap?,
    rootCoordinates: LayoutCoordinates?,
    glassTint: Color,
    reduceAnimations: Boolean,
    focusRequester: FocusRequester,
    onFocus: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    FocusSurface(
        onClick = onClick,
        modifier = modifier.focusRequester(focusRequester).then(onFocus).heightIn(min = 76.dp),
        shape = shape,
        reduceAnimations = reduceAnimations,
        baseColor = Color.White.copy(alpha = .08f),
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                artwork != null -> Image(bitmap = artwork.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
                item != null -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF203951), Color(0xFF10192E)))))
                else -> FrostedSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = shape,
                    wallpaper = wallpaper,
                    glassBitmap = glassBitmap,
                    rootCoordinates = rootCoordinates,
                    tint = glassTint,
                ) { }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .05f), Color.Black.copy(alpha = .72f)))))
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val title = when {
                    item != null -> item.title
                    missingPermission -> "Enable notification access"
                    else -> "Ready when you are"
                }
                val subtitle = when {
                    item != null -> listOfNotNull(item.appName, item.subtitle).joinToString(" · ")
                    missingPermission -> "Show recent media from your apps"
                    else -> "Play something to fill this card"
                }
                androidx.tv.material3.Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                androidx.tv.material3.Text(subtitle, color = Color.White.copy(alpha = .74f), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HomeAppTile(
    displayName: String,
    icon: Bitmap,
    modifier: Modifier,
    iconSize: androidx.compose.ui.unit.Dp,
    settings: LauncherSettings,
    reduceAnimations: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape,
) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        reduceAnimations = reduceAnimations,
        baseColor = Color.White.copy(alpha = .025f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = displayName,
                modifier = Modifier.size(iconSize.coerceAtMost(92.dp)).clip(shape),
                contentScale = ContentScale.Fit,
            )
            if (settings.showAppLabels) {
                androidx.tv.material3.Text(
                    displayName,
                    color = contentColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    fontFamily = fontFamilyFor(settings.fontFamily),
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentAppTile(
    displayName: String,
    icon: Bitmap,
    modifier: Modifier,
    reduceAnimations: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        reduceAnimations = reduceAnimations,
        baseColor = Color.White.copy(alpha = .07f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(bitmap = icon.asImageBitmap(), contentDescription = displayName, modifier = Modifier.size(32.dp), contentScale = ContentScale.Fit)
            androidx.tv.material3.Text(displayName, color = contentColor, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<LauncherApp>,
    favourites: List<String>,
    renamedApps: Map<String, String>,
    reduceAnimations: Boolean,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val visible = remember(apps, search) { apps.filter { it.label.contains(search, ignoreCase = true) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth(.86f).heightIn(max = 620.dp)
                .background(Color(0xF2182941), RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.tv.material3.Text("Add favourites", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = search,
                onValueChange = { search = it.take(48) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(14.dp),
                decorationBox = { inner ->
                    Box {
                        if (search.isBlank()) androidx.tv.material3.Text("Search installed apps", color = Color.White.copy(alpha = .5f), fontSize = 18.sp)
                        inner()
                    }
                },
            )
            androidx.tv.material3.Text("Select apps to add or remove. OK toggles a favourite; Back closes this picker.", color = Color.White.copy(alpha = .68f), fontSize = 13.sp)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(visible, key = { it.packageName }) { app ->
                    val selected = app.packageName in favourites
                    FocusSurface(
                        onClick = { onToggle(app.packageName) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        reduceAnimations = reduceAnimations,
                        shape = RoundedCornerShape(15.dp),
                        selected = selected,
                        baseColor = Color.White.copy(alpha = .06f),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(bitmap = app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(42.dp), contentScale = ContentScale.Fit)
                            androidx.tv.material3.Text(renamedApps[app.packageName] ?: app.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            androidx.tv.material3.Text(if (selected) "Added  ✓" else "Add  +", color = if (selected) Color(0xFF9FE4C0) else Color(0xFFBFEAFF), fontSize = 14.sp)
                        }
                    }
                }
            }
            androidx.tv.material3.Text("BACK  ·  Done", color = Color.White.copy(alpha = .65f), fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun ActionListDialog(
    title: String,
    actions: List<MenuAction>,
    reduceAnimations: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(.78f)
                .background(Color(0xF2192B45), RoundedCornerShape(26.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            androidx.tv.material3.Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            actions.forEach { action ->
                FocusSurface(
                    onClick = action.action,
                    modifier = Modifier.fillMaxWidth().height(if (action.subtitle == null) 54.dp else 66.dp),
                    reduceAnimations = reduceAnimations,
                    shape = RoundedCornerShape(14.dp),
                    baseColor = Color.White.copy(alpha = .08f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
                        androidx.tv.material3.Text(action.title, color = Color.White, fontSize = 16.sp)
                        action.subtitle?.let { androidx.tv.material3.Text(it, color = Color.White.copy(alpha = .65f), fontSize = 11.sp) }
                    }
                }
            }
            androidx.tv.material3.Text("Back to close", color = Color.White.copy(alpha = .55f), fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun rememberConnectionStatus(context: Context): ConnectionStatus {
    var status by remember { mutableStateOf(ConnectionStatus("offline", false, false)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun update() {
            val network = manager.activeNetwork
            val capabilities = network?.let(manager::getNetworkCapabilities)
            status = statusFrom(capabilities)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = update()
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        update()
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return status
}

private fun statusFrom(capabilities: NetworkCapabilities?): ConnectionStatus {
    if (capabilities == null) return ConnectionStatus("offline", false, false)
    val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionStatus("ethernet", true, validated)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionStatus("wifi", true, validated)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionStatus("network", true, validated)
        else -> ConnectionStatus("offline", false, false)
    }
}

private fun ConnectionStatus.label(): String = when {
    kind == "ethernet" && validated -> "Ethernet"
    kind == "ethernet" -> "Ethernet · no internet"
    kind == "wifi" && validated -> "Wi-Fi"
    kind == "wifi" -> "Wi-Fi · no internet"
    kind == "network" && validated -> "Online"
    kind == "network" -> "No internet"
    else -> "Offline"
}

private fun ConnectionStatus.iconKind(): String = when {
    kind == "ethernet" -> "ethernet"
    connected && validated -> "wifi"
    else -> "offline"
}

private fun formattedTime(): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())

private suspend fun requestSavedFocus(
    lastKey: String,
    requesters: Map<String, FocusRequester>,
    settings: LauncherSettings,
    favourites: List<LauncherApp>,
    dockApps: List<LauncherApp>,
) {
    val valid = when {
        lastKey.startsWith("favorite:") -> favourites.any { it.packageName == lastKey.removePrefix("favorite:") }
        lastKey.startsWith("dock:") -> dockApps.any { it.packageName == lastKey.removePrefix("dock:") }
        lastKey.startsWith("recent:") -> settings.recentAppsEnabled && settings.recentPackages.any { "recent:$it" == lastKey }
        lastKey.startsWith("watch:") -> settings.showWatching
        lastKey == "voice" -> requesters.containsKey("voice")
        else -> true
    }
    val candidates = buildList {
        if (valid) requesters[lastKey]?.let { add(it) }
        favourites.firstOrNull()?.let { requesters["favorite:${it.packageName}"] }?.let { add(it) }
        requesters["settings"]?.let { add(it) }
    }.distinct()
    candidates.forEach { requester ->
        val focused = runCatching { requester.requestFocus() }.getOrDefault(false)
        if (focused) return
    }
}

private fun readArtwork(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / (sample * 2) > 560 && bounds.outHeight / (sample * 2) > 320) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
}.getOrNull()

private fun isLightBitmap(bitmap: Bitmap): Boolean {
    if (bitmap.width <= 0 || bitmap.height <= 0) return false
    val xs = 5
    val ys = 5
    var total = 0L
    var count = 0
    for (x in 0 until xs) for (y in 0 until ys) {
        val px = ((x + 0.5f) * bitmap.width / xs).toInt().coerceIn(0, bitmap.width - 1)
        val py = ((y + 0.5f) * bitmap.height / ys).toInt().coerceIn(0, bitmap.height - 1)
        val color = bitmap.getPixel(px, py)
        total += (android.graphics.Color.red(color) * 299 + android.graphics.Color.green(color) * 587 + android.graphics.Color.blue(color) * 114) / 1000
        count++
    }
    return total / count > 142
}

private fun iconShape(settings: LauncherSettings): androidx.compose.ui.graphics.Shape = when (settings.iconShape) {
    "square" -> RoundedCornerShape(5.dp)
    "more-rounded" -> RoundedCornerShape(40.dp)
    "circle" -> CircleShape
    else -> RoundedCornerShape(percent = (settings.cornerRoundness * 100).toInt().coerceIn(0, 48))
}

private fun showMessage(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
