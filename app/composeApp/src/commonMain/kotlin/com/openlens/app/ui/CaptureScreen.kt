package com.openlens.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.camera.CameraPreview
import com.openlens.app.camera.rememberCameraController
import com.openlens.app.picker.rememberImagePickerLauncher
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.util.decodeImageBitmap
import kotlinx.coroutines.delay

/** Home: full-bleed camera with a cyan shutter that captures a frame. The gallery button on the
 * shutter's left imports an existing photo through the platform picker instead. */
@Composable
fun CaptureScreen(onCaptured: (ByteArray) -> Unit) {
    val controller = rememberCameraController()

    // Guards against firing overlapping captures (rapid taps) and gives feedback when the camera
    // hands back nothing — e.g. a tap while it's still warming up.
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(error) {
        if (error != null) {
            delay(2500)
            error = null
        }
    }

    // A successful pick doesn't navigate straight away: the bytes are held here while the reveal
    // animation grows the image out of the gallery button, then onCaptured fires.
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    var galleryOrigin by remember { mutableStateOf<Offset?>(null) }
    val galleryPicker = rememberImagePickerLauncher { bytes ->
        if (bytes != null) picked = bytes
        else error = "Couldn't load that image — try again"
    }
    val busy = capturing || picked != null

    Box(Modifier.fillMaxSize().background(OpenLensColors.Bg)) {
        CameraPreview(controller, Modifier.fillMaxSize())

        Text(
            text = "OpenLens",
            color = OpenLensColors.TextHi,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            error?.let { message ->
                Text(
                    text = message,
                    color = OpenLensColors.TextLo,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
            // The shutter stays optically centered; the gallery button sits off to its left.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GalleryButton(
                    enabled = !busy,
                    onClick = { galleryPicker.launch() },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 44.dp)
                        .onGloballyPositioned { galleryOrigin = it.boundsInRoot().center },
                )
                ShutterButton(
                    enabled = !busy,
                    onClick = {
                        capturing = true
                        error = null
                        controller.takePicture { bytes ->
                            capturing = false
                            if (bytes != null) onCaptured(bytes)
                            else error = "Couldn't capture — try again"
                        }
                    },
                )
            }
        }

        picked?.let { bytes ->
            GalleryReveal(
                bytes = bytes,
                origin = galleryOrigin,
                onFinished = { onCaptured(bytes) },
            )
        }
    }
}

/**
 * Circular reveal of a just-picked gallery image growing out of the gallery button: the image
 * sits fullscreen while a clip circle expands from the button to the farthest screen corner,
 * ringed by a fading white rim (the shutter's ping language) and settling out of a slight zoom.
 * [onFinished] fires once fully open, so navigation lands on a pixel-identical scanning frame
 * and the actual screen swap stays invisible.
 */
@Composable
private fun GalleryReveal(bytes: ByteArray, origin: Offset?, onFinished: () -> Unit) {
    val image = remember(bytes) { decodeImageBitmap(bytes) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(bytes) {
        // No recorded button position means nothing to grow from — hand off immediately.
        if (origin != null) {
            progress.animateTo(1f, animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing))
        }
        onFinished()
    }
    if (origin == null) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val p = progress.value
                val startRadius = 24.dp.toPx()
                val endRadius = listOf(
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                ).maxOf { corner -> (corner - origin).getDistance() }
                val radius = startRadius + (endRadius - startRadius) * p
                val window = Path().apply { addOval(Rect(center = origin, radius = radius)) }
                clipPath(window) { this@drawWithContent.drawContent() }
                if (p < 1f) {
                    drawCircle(
                        color = OpenLensColors.TextHi,
                        radius = radius,
                        center = origin,
                        alpha = 0.7f * (1f - p),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            },
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Lambda form keeps the per-frame scale in the draw phase (no recomposition).
                    val settle = 1.12f - 0.12f * progress.value
                    scaleX = settle
                    scaleY = settle
                },
        )
    }
}

/**
 * Round scrim button with a hand-drawn photo glyph (frame, sun, mountains) that opens the
 * platform gallery picker. Mirrors the shutter's press-scale feedback and disabled dimming.
 */
@Composable
private fun GalleryButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "galleryScale",
    )
    val dim by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        label = "galleryDim",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .alpha(dim)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(width = 1.dp, color = OpenLensColors.TextLo.copy(alpha = 0.4f), shape = CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            // Inset the frame so its stroke isn't clipped at the canvas bounds.
            val inset = stroke.width / 2
            drawRoundRect(
                color = OpenLensColors.TextHi,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = stroke,
            )
            drawCircle(
                color = OpenLensColors.TextHi,
                radius = 1.6.dp.toPx(),
                center = Offset(size.width * 0.34f, size.height * 0.32f),
            )
            val mountains = Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.8f)
                lineTo(size.width * 0.4f, size.height * 0.48f)
                lineTo(size.width * 0.58f, size.height * 0.66f)
                lineTo(size.width * 0.72f, size.height * 0.54f)
                lineTo(size.width * 0.92f, size.height * 0.8f)
            }
            drawPath(path = mountains, color = OpenLensColors.TextHi, style = stroke)
        }
    }
}

/**
 * Neutral glassy shutter (dark scrim + subtle border + white core, matching the gallery button)
 * that reacts to the tap: it scales down while pressed (springing back on release), and fires a
 * one-shot ring "ping" outward on each capture as confirmation. Dims and stops responding while a
 * capture is already in flight.
 */
@Composable
private fun ShutterButton(enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "shutterScale",
    )
    val dim by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        label = "shutterDim",
    )

    // Bumped on every capture; each bump replays the expanding/fading ring.
    var pingKey by remember { mutableStateOf(0) }
    val ping = remember { Animatable(0f) }
    LaunchedEffect(pingKey) {
        if (pingKey > 0) {
            ping.snapTo(0f)
            ping.animateTo(1f, animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing))
        }
    }

    // Box is larger than the 74.dp button so the expanding ring has room and isn't clipped.
    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val p = ping.value
            if (p > 0f && p < 1f) {
                val baseRadius = 37.dp.toPx() // half of the 74.dp shutter
                drawCircle(
                    color = OpenLensColors.TextHi,
                    radius = baseRadius * (1f + 0.9f * p),
                    alpha = 0.5f * (1f - p),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(74.dp)
                .scale(scale)
                .alpha(dim)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .border(
                    width = 1.5.dp,
                    color = OpenLensColors.TextLo.copy(alpha = 0.4f),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = {
                        pingKey++
                        onClick()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Solid white core — the capture affordance, kept neutral to match the gallery button.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(OpenLensColors.TextHi),
            )
        }
    }
}
