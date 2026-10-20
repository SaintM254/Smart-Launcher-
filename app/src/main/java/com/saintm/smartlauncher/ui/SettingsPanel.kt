package com.saintm.smartlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import com.saintm.smartlauncher.BuildConfig
import com.saintm.smartlauncher.data.LauncherApp
import com.saintm.smartlauncher.data.LauncherPreferences
import com.saintm.smartlauncher.data.LauncherSettings
import com.saintm.smartlauncher.util.WallpaperFactory
import com.saintm.smartlauncher.util.isHomeKeyServiceEnabled
import com.saintm.smartlauncher.util.canOpenTvInputs
import kotlinx.coroutines.launch

private enum class SettingsPage(val title: String) {
    MAIN("System settings"), APPS("Apps & icons"), MEDIA("Watching apps"), PRIVACY("Why media access?"), ABOUT("About Smart Launcher"),
}

@Composable
fun SettingsPanel(
    visible: Boolean,
    context: Context,
    preferences: LauncherPreferences,
    settings: LauncherSettings,
    apps: List<LauncherApp>,
    wallpaper: Bitmap?,
    glassBitmap: Bitmap?,
    rootCoordinates: LayoutCoordinates?,
    onClose: () -> Unit,
    onChooseWallpaper: () -> Unit,
    onChooseCustomIcon: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRerunSetup: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    var editingApp by remember { mutableStateOf<LauncherApp?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val closeFocus = remember { FocusRequester() }

    BackHandler(enabled = visible && (editingApp != null || confirmReset || page != SettingsPage.MAIN)) {
        when {
            editingApp != null -> editingApp = null
            confirmReset -> confirmReset = false
            else -> page = SettingsPage.MAIN
        }
    }
    LaunchedEffect(visible, page) {
        if (visible) runCatching { closeFocus.requestFocus() }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = androidx.compose.animation.slideInHorizontally(
            animationSpec = androidx.compose.animation.core.tween(if (settings.reduceAnimations) 0 else 210),
            initialOffsetX = { it },
        ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(if (settings.reduceAnimations) 0 else 180)),
        exit = androidx.compose.animation.slideOutHorizontally(
            animationSpec = androidx.compose.animation.core.tween(if (settings.reduceAnimations) 0 else 190),
            targetOffsetX = { it },
        ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (settings.reduceAnimations) 0 else 160)),
    ) {
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                val isBack = event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                    event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE
                if (isBack && event.type == KeyEventType.KeyUp) {
                    when {
                        editingApp != null -> editingApp = null
                        confirmReset -> confirmReset = false
                        page != SettingsPage.MAIN -> page = SettingsPage.MAIN
                        else -> onClose()
                    }
                    true
                } else isBack
            },
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.36f))
                    .clickable(onClick = onClose),
            )
            FrostedSurface(
                modifier = Modifier.align(Alignment.CenterEnd)
                    .widthIn(max = 540.dp)
                    .fillMaxHeight()
                    .padding(18.dp),
                shape = RoundedCornerShape(30.dp),
                wallpaper = wallpaper,
                glassBitmap = glassBitmap,
                rootCoordinates = rootCoordinates,
                tint = Color(0xD9101D31),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            androidx.tv.material3.Text(
                                text = page.title,
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = fontFamilyFor(settings.fontFamily),
                            )
                            if (page == SettingsPage.MAIN) {
                                androidx.tv.material3.Text("PERSONALIZE YOUR HOME", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 1.3.sp)
                            }
                        }
                        FocusSurface(
                            onClick = onClose,
                            modifier = Modifier.size(44.dp).focusRequester(closeFocus),
                            shape = CircleShape,
                            reduceAnimations = settings.reduceAnimations,
                            baseColor = Color.White.copy(alpha = 0.10f),
                        ) {
                            SimpleIcon("close", Modifier.size(18.dp), color = Color.White)
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        when (page) {
                            SettingsPage.MAIN -> MainSettingsPage(
                                context = context,
                                preferences = preferences,
                                settings = settings,
                                onWallpaper = onChooseWallpaper,
                                onApps = { page = SettingsPage.APPS },
                                onMedia = { page = SettingsPage.MEDIA },
                                onPrivacy = { page = SettingsPage.PRIVACY },
                                onAbout = { page = SettingsPage.ABOUT },
                                onExport = onExport,
                                onImport = onImport,
                                onRerunSetup = onRerunSetup,
                                onReset = { confirmReset = true },
                                onMessage = onMessage,
                                reduceAnimations = settings.reduceAnimations,
                            )
                            SettingsPage.APPS -> AppsSettingsPage(
                                apps = apps,
                                settings = settings,
                                onEdit = { editingApp = it },
                            )
                            SettingsPage.MEDIA -> MediaSettingsPage(
                                apps = apps,
                                settings = settings,
                                preferences = preferences,
                                reduceAnimations = settings.reduceAnimations,
                            )
                            SettingsPage.PRIVACY -> PrivacyPage(
                                context = context,
                                onOpenMediaSettings = {
                                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                },
                                reduceAnimations = settings.reduceAnimations,
                            )
                            SettingsPage.ABOUT -> AboutPage(reduceAnimations = settings.reduceAnimations)
                        }
                    }
                }
            }
        }
    }

    editingApp?.let { app ->
        AppEditorDialog(
            app = app,
            settings = settings,
            preferences = preferences,
            reduceAnimations = settings.reduceAnimations,
            onChooseIcon = { onChooseCustomIcon(app.packageName); editingApp = null },
            onDismiss = { editingApp = null },
            onMessage = onMessage,
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset Smart Launcher?",
            message = "This clears launcher preferences, favourites, and media history on this device. It does not remove apps or change Android's Home app setting.",
            confirmLabel = "Reset settings",
            onDismiss = { confirmReset = false },
            onConfirm = {
                scope.launch {
                    preferences.reset()
                    confirmReset = false
                    onMessage("Settings reset. Open setup again if you need help.")
                }
            },
        )
    }
}

@Composable
private fun MainSettingsPage(
    context: Context,
    preferences: LauncherPreferences,
    settings: LauncherSettings,
    onWallpaper: () -> Unit,
    onApps: () -> Unit,
    onMedia: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRerunSetup: () -> Unit,
    onReset: () -> Unit,
    onMessage: (String) -> Unit,
    reduceAnimations: Boolean,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("APPEARANCE")
        androidx.tv.material3.Text("Background", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WallpaperFactory.presets.forEach { preset ->
                FocusSurface(
                    onClick = { scope.launch { preferences.setWallpaper(preset.id) } },
                    modifier = Modifier.width(96.dp).height(52.dp),
                    selected = settings.wallpaperId == preset.id && settings.customWallpaperUri == null,
                    reduceAnimations = reduceAnimations,
                    shape = RoundedCornerShape(14.dp),
                    baseColor = Color(preset.top),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.fillMaxWidth().height(31.dp).padding(horizontal = 9.dp, vertical = 4.dp).background(Color(preset.bottom), RoundedCornerShape(8.dp)))
                        androidx.tv.material3.Text(preset.name, color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
        SettingAction(
            title = "Choose from storage",
            subtitle = if (settings.customWallpaperUri != null) "Custom wallpaper selected" else "Pick a photo; only this image is opened",
            trailing = "Browse  ›",
            reduceAnimations = reduceAnimations,
            onClick = onWallpaper,
        )

        SectionLabel("APPS & ICONS")
        SettingAction("Icon shape", trailing = settings.iconShape.displayShape(), reduceAnimations = reduceAnimations, onClick = {
            val shapes = listOf("square", "rounded", "more-rounded", "circle")
            scope.launch { preferences.setIconShape(shapes[(shapes.indexOf(settings.iconShape) + 1) % shapes.size]) }
        })
        StepperRow(
            title = "Corner roundness",
            value = "${(settings.cornerRoundness * 100).toInt()}%",
            onDecrease = { scope.launch { preferences.setCornerRoundness(settings.cornerRoundness - 0.05f) } },
            onIncrease = { scope.launch { preferences.setCornerRoundness(settings.cornerRoundness + 0.05f) } },
            reduceAnimations = reduceAnimations,
        )
        StepperRow(
            title = "Icon size",
            value = "${settings.iconSize} dp",
            onDecrease = { scope.launch { preferences.setIconSize(settings.iconSize - 4) } },
            onIncrease = { scope.launch { preferences.setIconSize(settings.iconSize + 4) } },
            reduceAnimations = reduceAnimations,
        )
        ToggleRow("Show app labels", settings.showAppLabels, reduceAnimations) {
            scope.launch { preferences.setShowAppLabels(!settings.showAppLabels) }
        }
        SettingAction("Manage apps, hide and rename", "Choose a custom icon or hide apps from the dock", "Open  ›", reduceAnimations, onApps)

        SectionLabel("FONT")
        androidx.tv.material3.Text(
            text = "Smart Launcher  ·  Aa Bb 123",
            color = Color.White.copy(alpha = 0.90f),
            fontFamily = fontFamilyFor(settings.fontFamily),
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system", "inter", "poppins", "montserrat", "playfair", "lora").forEach { font ->
                FocusSurface(
                    onClick = { scope.launch { preferences.setFontFamily(font) } },
                    modifier = Modifier.width(112.dp).height(54.dp),
                    selected = settings.fontFamily == font,
                    reduceAnimations = reduceAnimations,
                    shape = RoundedCornerShape(13.dp),
                    baseColor = Color.White.copy(alpha = 0.08f),
                ) {
                    androidx.tv.material3.Text(
                        text = fontDisplayName(font),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = fontFamilyFor(font),
                    )
                }
            }
        }

        SectionLabel("HOME SCREEN")
        ToggleRow("Continue watching cards", settings.showWatching, reduceAnimations) {
            scope.launch { preferences.setShowWatching(!settings.showWatching) }
        }
        StepperRow(
            title = "Visible favourites",
            value = settings.favouriteCount.toString(),
            onDecrease = { scope.launch { preferences.setFavouriteCount(settings.favouriteCount - 1) } },
            onIncrease = { scope.launch { preferences.setFavouriteCount(settings.favouriteCount + 1) } },
            reduceAnimations = reduceAnimations,
        )
        StepperRow(
            title = "Dock row size",
            value = "${settings.dockCapacity} visible",
            onDecrease = { scope.launch { preferences.setDockCapacity(settings.dockCapacity - 1) } },
            onIncrease = { scope.launch { preferences.setDockCapacity(settings.dockCapacity + 1) } },
            reduceAnimations = reduceAnimations,
        )
        ToggleRow("Recent apps row", settings.recentAppsEnabled, reduceAnimations) {
            scope.launch { preferences.setRecentAppsEnabled(!settings.recentAppsEnabled) }
        }

        SectionLabel("MEDIA & PERMISSIONS")
        SettingAction("Choose watching apps", "Filter which media apps can appear", "Choose  ›", reduceAnimations, onMedia)
        SettingAction("Why notification access?", trailing = "Learn  ›", reduceAnimations = reduceAnimations, onClick = onPrivacy)
        SettingAction("Clear watching history", trailing = "Clear  ›", reduceAnimations = reduceAnimations, onClick = {
            scope.launch { preferences.clearMediaHistory(); onMessage("Watching history cleared.") }
        })
        val accessibilityOn = isHomeKeyServiceEnabled(context)
        ToggleRow("Home key override", settings.homeOverrideEnabled, reduceAnimations) {
            scope.launch { preferences.setHomeOverride(!settings.homeOverrideEnabled) }
        }
        ToggleRow("Safe mode", settings.safeMode, reduceAnimations) {
            scope.launch {
                preferences.setSafeMode(!settings.safeMode)
                onMessage(if (settings.safeMode) "Safe mode is off. Home override can run again." else "Safe mode is on. Home override is paused.")
            }
        }
        if (!accessibilityOn) {
            androidx.tv.material3.Text(
                "The Home shortcut still needs the accessibility service enabled in Android settings.",
                color = Color(0xFFFFD69A),
                fontSize = 12.sp,
            )
        }
        SettingAction("Run first-time setup", trailing = "Open  ›", reduceAnimations = reduceAnimations, onClick = onRerunSetup)

        SectionLabel("DEVICE")
        if (canOpenTvInputs(context)) {
            SettingAction("TV inputs / HDMI", trailing = "Open  ›", reduceAnimations = reduceAnimations, onClick = {
                runCatching { context.startActivity(android.content.Intent("android.settings.TV_INPUT_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
        }
        SettingAction("Export settings", trailing = "Save JSON  ›", reduceAnimations = reduceAnimations, onClick = onExport)
        SettingAction("Import settings", trailing = "Open JSON  ›", reduceAnimations = reduceAnimations, onClick = onImport)
        SettingAction("About", trailing = "Version ${BuildConfig.VERSION_NAME}  ›", reduceAnimations = reduceAnimations, onClick = onAbout)
        SettingAction("Reset to default", trailing = "Reset  ›", reduceAnimations = reduceAnimations, onClick = onReset)
        androidx.tv.material3.Text(
            "Animations: ${if (settings.reduceAnimations) "reduced" else "standard"}",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        ToggleRow("Reduce animations", settings.reduceAnimations, reduceAnimations) {
            scope.launch { preferences.setReduceAnimations(!settings.reduceAnimations) }
        }
    }
}

@Composable
private fun AppsSettingsPage(
    apps: List<LauncherApp>,
    settings: LauncherSettings,
    onEdit: (LauncherApp) -> Unit,
) {
    if (apps.isEmpty()) {
        androidx.tv.material3.Text("No launchable apps were found yet. Try again after Android finishes loading your apps.", color = Color.White, fontSize = 15.sp)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(apps, key = { it.packageName }) { app ->
            FocusSurface(
                onClick = { onEdit(app) },
                modifier = Modifier.fillMaxWidth().height(70.dp),
                reduceAnimations = settings.reduceAnimations,
                shape = RoundedCornerShape(16.dp),
                baseColor = Color.White.copy(alpha = 0.07f),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(bitmap = app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(42.dp), contentScale = ContentScale.Fit)
                    Column(Modifier.weight(1f)) {
                        androidx.tv.material3.Text(settings.renamedApps[app.packageName] ?: app.label, color = Color.White, fontSize = 15.sp, maxLines = 1)
                        androidx.tv.material3.Text(app.packageName, color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, maxLines = 1)
                    }
                    androidx.tv.material3.Text(if (app.packageName in settings.hiddenPackages) "Hidden" else "Edit  ›", color = Color(0xFFBFEAFF), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MediaSettingsPage(
    apps: List<LauncherApp>,
    settings: LauncherSettings,
    preferences: LauncherPreferences,
    reduceAnimations: Boolean,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        androidx.tv.material3.Text(
            if (settings.mediaFilterEnabled) "Only selected apps can appear in the three cards." else "All media apps are allowed. Choose apps below to make a filter.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        SettingAction(
            title = "Allow all media apps",
            trailing = if (!settings.mediaFilterEnabled) "Selected" else "Allow all  ›",
            reduceAnimations = reduceAnimations,
            onClick = { scope.launch { preferences.setMediaFilter(false, emptySet()) } },
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(apps, key = { it.packageName }) { app ->
                val allowed = !settings.mediaFilterEnabled || app.packageName in settings.allowedMediaPackages
                FocusSurface(
                    onClick = {
                        val current = if (settings.mediaFilterEnabled) settings.allowedMediaPackages else apps.map { it.packageName }.toSet()
                        val next = if (app.packageName in current) current - app.packageName else current + app.packageName
                        scope.launch { preferences.setMediaFilter(true, next) }
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    reduceAnimations = reduceAnimations,
                    shape = RoundedCornerShape(14.dp),
                    baseColor = Color.White.copy(alpha = 0.06f),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        androidx.tv.material3.Text(settings.renamedApps[app.packageName] ?: app.label, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        androidx.tv.material3.Text(if (allowed) "Allowed  ✓" else "Blocked", color = if (allowed) Color(0xFF9FE4C0) else Color.White.copy(alpha = .55f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyPage(context: Context, onOpenMediaSettings: () -> Unit, reduceAnimations: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        androidx.tv.material3.Text(
            "Smart Launcher asks for notification access only to discover media sessions from apps you already use. It reads a session's app name, title, subtitle, playback state and available artwork, then keeps a small local history; the home screen shows the latest three permitted apps.",
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        androidx.tv.material3.Text(
            "It does not read notification text, contacts, account details, or content from another app's screen. Android does not provide third-party launchers with other apps' official Watch Next catalog; MediaSession is the supported route used here.",
            color = Color.White.copy(alpha = 0.76f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        androidx.tv.material3.Text(
            "History and small artwork thumbnails stay in this app's private storage. Use Settings → Watching apps to filter sources, or clear the launcher data to remove everything.",
            color = Color.White.copy(alpha = 0.76f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        SettingAction("Open Android notification access", trailing = "Open  ›", reduceAnimations = reduceAnimations, onClick = onOpenMediaSettings)
    }
}

@Composable
private fun AboutPage(reduceAnimations: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.tv.material3.Text("Smart Launcher", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        androidx.tv.material3.Text("Version ${BuildConfig.VERSION_NAME}  ·  ${BuildConfig.APPLICATION_ID}", color = Color(0xFFBFEAFF), fontSize = 14.sp)
        androidx.tv.material3.Text(
            "A lightweight Android TV home screen. No ads, tracking SDKs, analytics, or overlay permission. Network status is read from Android's connectivity service; app icons and media history stay on this device.",
            color = Color.White.copy(alpha = 0.76f),
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        androidx.tv.material3.Text("TV remote controls: arrows move focus · OK opens or selects · Back closes a panel · hold Back for five seconds on Home to enable Safe mode · Menu opens settings.", color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
        androidx.tv.material3.Text("Open-source fonts: Inter, Poppins, Montserrat, Playfair Display and Lora (SIL Open Font License).", color = Color.White.copy(alpha = .62f), fontSize = 12.sp)
        androidx.tv.material3.Text("Reduce animations: ${if (reduceAnimations) "on" else "off"}", color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
    }
}

@Composable
private fun AppEditorDialog(
    app: LauncherApp,
    settings: LauncherSettings,
    preferences: LauncherPreferences,
    reduceAnimations: Boolean,
    onChooseIcon: () -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var name by remember(app.packageName, settings.renamedApps) { mutableStateOf(settings.renamedApps[app.packageName] ?: app.label) }
    var hidden by remember(app.packageName, settings.hiddenPackages) { mutableStateOf(app.packageName in settings.hiddenPackages) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(0.86f)
                .background(Color(0xF21A2940), RoundedCornerShape(26.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.tv.material3.Text("Edit ${app.label}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(48) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = .11f), RoundedCornerShape(12.dp)).padding(14.dp),
                decorationBox = { inner ->
                    Box {
                        if (name.isEmpty()) androidx.tv.material3.Text("App label", color = Color.White.copy(alpha = .46f), fontSize = 18.sp)
                        inner()
                    }
                },
            )
            ToggleRow("Hide this app from the dock", hidden, reduceAnimations) { hidden = !hidden }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingAction("Choose custom icon", trailing = "Pick  ›", reduceAnimations = reduceAnimations, onClick = onChooseIcon, modifier = Modifier.weight(1f))
                if (app.packageName in settings.customIcons) {
                    SettingAction("Clear custom icon", trailing = "Remove", reduceAnimations = reduceAnimations, onClick = {
                        scope.launch { preferences.setCustomIcon(app.packageName, null); onMessage("Custom icon cleared.") }
                    }, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingAction("Cancel", trailing = "Back", reduceAnimations = reduceAnimations, onClick = onDismiss, modifier = Modifier.weight(1f))
                SettingAction("Save", trailing = "OK  ›", reduceAnimations = reduceAnimations, onClick = {
                    scope.launch {
                        preferences.setAppName(app.packageName, name.takeIf { it.trim() != app.label })
                        val hiddenNow = if (hidden) settings.hiddenPackages + app.packageName else settings.hiddenPackages - app.packageName
                        preferences.setHiddenPackages(hiddenNow)
                        onDismiss()
                    }
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 500.dp).fillMaxWidth(0.82f)
                .background(Color(0xF21A2940), RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.tv.material3.Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            androidx.tv.material3.Text(message, color = Color.White.copy(alpha = .78f), fontSize = 15.sp, lineHeight = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingAction("Cancel", trailing = "Back", reduceAnimations = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                SettingAction(confirmLabel, trailing = "OK  ›", reduceAnimations = false, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingAction(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    reduceAnimations: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(if (subtitle == null) 48.dp else 62.dp),
        reduceAnimations = reduceAnimations,
        shape = RoundedCornerShape(14.dp),
        baseColor = Color.White.copy(alpha = 0.065f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                androidx.tv.material3.Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                subtitle?.let { androidx.tv.material3.Text(it, color = Color.White.copy(alpha = .58f), fontSize = 10.sp, maxLines = 2) }
            }
            trailing?.let { androidx.tv.material3.Text(it, color = Color.White.copy(alpha = .76f), fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, value: Boolean, reduceAnimations: Boolean, onClick: () -> Unit) {
    SettingAction(title, trailing = if (value) "ON  ✓" else "OFF", reduceAnimations = reduceAnimations, onClick = onClick)
}

@Composable
private fun StepperRow(
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    reduceAnimations: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.tv.material3.Text(title, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2)
        FocusSurface(onClick = onDecrease, modifier = Modifier.size(40.dp), reduceAnimations = reduceAnimations, shape = CircleShape) {
            androidx.tv.material3.Text("−", color = Color.White, fontSize = 22.sp)
        }
        androidx.tv.material3.Text(value, color = Color(0xFFBFEAFF), fontSize = 12.sp)
        FocusSurface(onClick = onIncrease, modifier = Modifier.size(40.dp), reduceAnimations = reduceAnimations, shape = CircleShape) {
            androidx.tv.material3.Text("+", color = Color.White, fontSize = 20.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    androidx.tv.material3.Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 1.dp))
}

private fun String.displayShape(): String = when (this) {
    "square" -> "Square"
    "more-rounded" -> "More rounded"
    "circle" -> "Circle"
    else -> "Rounded"
}
