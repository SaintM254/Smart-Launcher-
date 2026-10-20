package com.saintm.smartlauncher.ui

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(22.dp),
    selected: Boolean = false,
    reduceAnimations: Boolean = false,
    baseColor: Color = Color.White.copy(alpha = 0.11f),
    focusColor: Color = Color(0xFF5CCBFA),
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var confirmKeyDown by remember { mutableStateOf(false) }
    var longPressHandled by remember { mutableStateOf(false) }
    var confirmPressedAt by remember { mutableLongStateOf(0L) }
    val keyScope = rememberCoroutineScope()
    val animatedScale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = tween(if (reduceAnimations) 0 else 180),
        label = "tv-focus-scale",
    )
    Box(
        modifier = modifier
            .shadow(if (focused || selected) 12.dp else 2.dp, shape)
            .clip(shape)
            .background(if (focused || selected) focusColor.copy(alpha = 0.20f) else baseColor)
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = if (focused || selected) focusColor else Color.White.copy(alpha = 0.22f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .then(if (reduceAnimations) Modifier else Modifier.graphicsScale(animatedScale))
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val isConfirm = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == AndroidKeyEvent.KEYCODE_ENTER || keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                if (!isConfirm) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            val startedAt = SystemClock.elapsedRealtime()
                            confirmPressedAt = startedAt
                            confirmKeyDown = true
                            longPressHandled = false
                            if (onLongClick != null) keyScope.launch {
                                delay(560L)
                                if (confirmKeyDown && confirmPressedAt == startedAt && !longPressHandled) {
                                    longPressHandled = true
                                    onLongClick()
                                }
                            }
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        if (confirmKeyDown && !longPressHandled) onClick()
                        confirmKeyDown = false
                        true
                    }
                    else -> true
                }
            }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .focusable(),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private fun Modifier.graphicsScale(scale: Float): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
}

/** A clipped duplicate of the static wallpaper gives Android 12+ a hardware blur and older TVs
 * a pre-blurred bitmap generated once when that wallpaper was selected. */
@Composable
fun FrostedSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    wallpaper: Bitmap?,
    glassBitmap: Bitmap?,
    rootCoordinates: LayoutCoordinates?,
    tint: Color = Color.White.copy(alpha = 0.15f),
    content: @Composable BoxScope.() -> Unit,
) {
    var localCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val blurSource = if (Build.VERSION.SDK_INT >= 31) wallpaper else glassBitmap
    val sourceImage: ImageBitmap? = remember(blurSource) { blurSource?.asImageBitmap() }
    val rootSize = rootCoordinates?.size
    val localOffset = remember(localCoordinates, rootCoordinates) {
        if (localCoordinates == null || rootCoordinates == null) null
        else runCatching { localCoordinates!!.localPositionOf(rootCoordinates, Offset.Zero) }.getOrNull()
    }

    Box(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { localCoordinates = it },
    ) {
        if (sourceImage != null && rootSize != null && localOffset != null) {
            Box(Modifier.matchParentSize().clip(shape)) {
                Image(
                    bitmap = sourceImage,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset { IntOffset(localOffset.x.roundToInt(), localOffset.y.roundToInt()) }
                        .size(
                            width = with(density) { rootSize.width.toDp() },
                            height = with(density) { rootSize.height.toDp() },
                        )
                        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier),
                )
            }
        }
        Box(Modifier.matchParentSize().background(tint))
        content()
    }
}

@Composable
fun SimpleIcon(
    kind: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Float = 2.6f,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (kind) {
            "wifi", "offline" -> drawWifi(w, h, color, strokeWidth, kind == "offline")
            "ethernet" -> {
                drawRoundRect(color, topLeft = Offset(w * .18f, h * .18f), size = Size(w * .64f, h * .54f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .08f), style = Stroke(strokeWidth))
                drawLine(color, Offset(w * .34f, h * .73f), Offset(w * .34f, h * .9f), strokeWidth)
                drawLine(color, Offset(w * .5f, h * .73f), Offset(w * .5f, h * .9f), strokeWidth)
                drawLine(color, Offset(w * .66f, h * .73f), Offset(w * .66f, h * .9f), strokeWidth)
            }
            "settings" -> drawSettings(w, h, color, strokeWidth)
            "voice" -> {
                drawRoundRect(color, topLeft = Offset(w * .39f, h * .08f), size = Size(w * .22f, h * .54f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .12f))
                drawArc(color, 0f, 180f, false, Offset(w * .25f, h * .28f), Size(w * .5f, h * .5f), style = Stroke(strokeWidth))
                drawLine(color, Offset(w * .5f, h * .78f), Offset(w * .5f, h * .94f), strokeWidth)
                drawLine(color, Offset(w * .34f, h * .94f), Offset(w * .66f, h * .94f), strokeWidth)
            }
            "close" -> {
                drawLine(color, Offset(w * .24f, h * .24f), Offset(w * .76f, h * .76f), strokeWidth)
                drawLine(color, Offset(w * .76f, h * .24f), Offset(w * .24f, h * .76f), strokeWidth)
            }
            else -> {
                drawCircle(color, radius = w * .08f, center = Offset(w * .5f, h * .5f))
            }
        }
    }
}

private fun DrawScope.drawWifi(w: Float, h: Float, color: Color, stroke: Float, offline: Boolean) {
    val center = Offset(w * .5f, h * .74f)
    drawArc(color, 215f, 110f, false, Offset(w * .08f, h * .05f), Size(w * .84f, h * .84f), style = Stroke(stroke))
    drawArc(color, 220f, 100f, false, Offset(w * .23f, h * .25f), Size(w * .54f, h * .54f), style = Stroke(stroke))
    drawArc(color, 225f, 90f, false, Offset(w * .38f, h * .46f), Size(w * .24f, h * .24f), style = Stroke(stroke))
    drawCircle(color, radius = stroke * 1.4f, center = Offset(center.x, h * .87f))
    if (offline) {
        drawLine(color, Offset(w * .22f, h * .22f), Offset(w * .78f, h * .78f), stroke)
    }
}

private fun DrawScope.drawSettings(w: Float, h: Float, color: Color, stroke: Float) {
    val center = Offset(w * .5f, h * .5f)
    repeat(8) { index ->
        rotate(index * 45f, center) {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * .43f, h * .06f),
                size = Size(w * .14f, h * .24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .03f),
            )
        }
    }
    drawCircle(color, radius = w * .32f, center = center, style = Stroke(stroke))
    drawCircle(color, radius = w * .1f, center = center)
}
