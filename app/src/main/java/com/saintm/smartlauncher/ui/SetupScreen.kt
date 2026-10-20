package com.saintm.smartlauncher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.saintm.smartlauncher.data.LauncherPreferences
import com.saintm.smartlauncher.util.isDefaultHome
import com.saintm.smartlauncher.util.isHomeKeyServiceEnabled
import com.saintm.smartlauncher.util.isMediaListenerEnabled
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    context: Context,
    preferences: LauncherPreferences,
    onDone: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    val accessibilityEnabled = remember(refresh) { isHomeKeyServiceEnabled(context) }
    val notificationsEnabled = remember(refresh) { isMediaListenerEnabled(context) }
    val defaultHome = remember(refresh) { isDefaultHome(context) }
    val coroutineScope = rememberCoroutineScope()
    val firstStepFocus = remember { FocusRequester() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstStepFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF06152D), Color(0xFF123C61), Color(0xFF08152D))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        androidx.tv.material3.Text(
            text = "Welcome to Smart Launcher",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamilyFor("inter"),
        )
        androidx.tv.material3.Text(
            text = "A simple home screen made for your TV remote. These setup steps are optional; skipping any of them will not crash the app.",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontFamily = fontFamilyFor("inter"),
        )

        SetupStep(
            number = "1",
            title = "Home button shortcut",
            description = "Enable the small accessibility service so the remote's Home button can return here. It only filters the Home key and never reads screen content.",
            complete = accessibilityEnabled,
            firstStep = true,
            focusRequester = firstStepFocus,
            onClick = { openSystemPage(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
        SetupStep(
            number = "2",
            title = "Continue watching cards",
            description = "Allow notification access so Smart Launcher can read media-session titles and artwork from supported apps. It does not read notification text.",
            complete = notificationsEnabled,
            onClick = { openSystemPage(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        )
        SetupStep(
            number = "3",
            title = "Choose your Home app",
            description = "Set Smart Launcher as the default Home app to start here after boot and whenever Home is pressed. You can change this later in system settings.",
            complete = defaultHome,
            onClick = { openSystemPage(context, Intent(Settings.ACTION_HOME_SETTINGS)) },
        )

        androidx.tv.material3.Text(
            text = "No 'display over other apps' permission is needed. Smart Launcher never draws over other apps.",
            color = Color(0xFFBFEAFF),
            fontSize = 16.sp,
            fontFamily = fontFamilyFor("inter"),
        )

        FocusSurface(
            onClick = {
                coroutineScope.launch {
                    preferences.setSetupComplete(true)
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            baseColor = Color(0xFF147BAA),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.tv.material3.Text("Continue to Smart Launcher", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                androidx.tv.material3.Text("OK  ›", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: String,
    title: String,
    description: String,
    complete: Boolean,
    firstStep: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(if (firstStep && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        baseColor = Color.White.copy(alpha = 0.10f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.tv.material3.Text(number, color = Color(0xFF5CCBFA), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.tv.material3.Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                androidx.tv.material3.Text(description, color = Color.White.copy(alpha = 0.76f), fontSize = 14.sp, lineHeight = 19.sp)
            }
            androidx.tv.material3.Text(
                text = if (complete) "Ready" else "Open settings  ›",
                color = if (complete) Color(0xFF9FE4C0) else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun openSystemPage(context: Context, intent: Intent) {
    runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
