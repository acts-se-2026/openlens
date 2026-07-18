package com.openlens.app.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.camera.CameraController
import com.openlens.app.picker.rememberImagePickerLauncher
import com.openlens.app.scan.ScanMode
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.util.BlurCheck
import com.openlens.app.util.decodeImageBitmap
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Most times a blurry camera shot is silently recaptured before the manual "too blurry" prompt is
 * shown. Bump this to try harder on its own; drop it to hand control back to the user sooner.
 */
private const val MAX_AUTO_RETAKES = 5

/** How long to let the camera settle between a rejected blurry shot and the next auto-retake. */
private const val AUTO_RETAKE_DELAY_MS = 300L

/** Grab a single still from the camera, suspending until it arrives (null if none came back). */
private suspend fun captureFrame(controller: CameraController): ByteArray? =
    suspendCancellableCoroutine { cont ->
        println("OL/gate captureFrame: calling takePicture")
        controller.takePicture {
            println("OL/gate captureFrame: callback got ${it?.size ?: -1} bytes")
            cont.resume(it)
        }
    }

/**
 * Silently reshoot a blurry camera frame, letting the camera settle between tries, until it reads
 * sharp or the [MAX_AUTO_RETAKES] budget runs out. [firstBlurry] is the frame that just failed; no UI
 * is shown while this runs. Returns the frame to act on paired with whether it's still blurry — sharp
 * (`false`) once a good frame lands, or the last frame with `true` if the budget/camera gives out,
 * leaving the caller to raise the manual prompt. Measures each frame it grabs exactly once.
 */
private suspend fun autoRetake(
    controller: CameraController,
    firstBlurry: ByteArray,
): Pair<ByteArray, Boolean> {
    var last = firstBlurry
    repeat(MAX_AUTO_RETAKES) { i ->
        delay(AUTO_RETAKE_DELAY_MS)
        val next = captureFrame(controller) ?: run {
            println("OL/gate autoRetake[$i]: null frame, giving up")
            return last to true
        }
        val score = BlurCheck.score(next)
        println("OL/gate autoRetake[$i]: score=$score threshold=${BlurCheck.THRESHOLD}")
        if (score == null || score >= BlurCheck.THRESHOLD) return next to false
        last = next
    }
    return last to true
}

/** Home: full-bleed camera with a neutral shutter that captures a frame. The gallery button on the
 * shutter's left imports an existing photo through the platform picker instead. The live preview is
 * owned by the caller ([controller]) and hoisted above this screen, so it survives the hop into
 * scanning; this screen only draws the controls over it. */
@Composable
fun CaptureScreen(
    controller: CameraController,
    selectedMode: ScanMode,
    onModeSelected: (ScanMode) -> Unit,
    onCaptured: (ByteArray) -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Guards against firing overlapping captures (rapid taps) and gives feedback when the camera
    // hands back nothing — e.g. a tap while it's still warming up.
    var capturing by remember { mutableStateOf(false) }
    // The blur gate runs between a capture/pick and navigation; freezes the controls while it does.
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(error) {
        if (error != null) {
            delay(2500)
            error = null
        }
    }

    // A successful pick doesn't navigate straight away: the bytes are held here while the reveal
    // animation grows the image out of the gallery button, then the blur gate runs.
    var picked by remember { mutableStateOf<ByteArray?>(null) }
    var galleryOrigin by remember { mutableStateOf<Offset?>(null) }
    // Set when a shot exhausts its auto-retakes and still reads blurry: shows the "too blurry" prompt
    // over the frame instead of navigating on. Retake clears it; Use anyway sends the shot through.
    var blurryBytes by remember { mutableStateOf<ByteArray?>(null) }
    val galleryPicker = rememberImagePickerLauncher { bytes ->
        if (bytes != null) picked = bytes
        else error = "Couldn't load that image — try again"
    }
    val busy = capturing || checking || picked != null || blurryBytes != null

    // Sharpness gate shared by both capture paths: measure the frame off the main thread, then route
    // it. A sharp frame is handed on; a blurry *camera* shot is passed to [autoRetake] to be reshot
    // silently; a blurry *gallery* pick can't be re-shot, so it goes straight to the manual prompt.
    // An undecodable image scores as "can't tell" (never blurry), so the gate can't hard-block a shot
    // it couldn't measure. Each distinct frame is measured exactly once (here or inside autoRetake).
    fun review(bytes: ByteArray, canRetake: Boolean) {
        checking = true
        scope.launch {
            val firstScore = BlurCheck.score(bytes)
            println("OL/gate review: canRetake=$canRetake firstScore=$firstScore threshold=${BlurCheck.THRESHOLD} size=${bytes.size}")
            val (finalBytes, blurry) = when {
                firstScore == null || firstScore >= BlurCheck.THRESHOLD -> bytes to false
                canRetake -> autoRetake(controller, bytes)
                else -> bytes to true
            }
            println("OL/gate review: done blurry=$blurry")
            capturing = false
            checking = false
            picked = null
            if (blurry) blurryBytes = finalBytes else onCaptured(finalBytes)
        }
    }

    // No background here: the caller's hoisted camera preview sits underneath and shows through.
    Box(Modifier.fillMaxSize()) {
        // Bottom scrim: keeps the mode strip and controls legible over bright or white scenes.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.5f to OpenLensColors.Bg.copy(alpha = 0.45f),
                            1f to OpenLensColors.Bg.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )

        Text(
            text = "OpenLens",
            color = OpenLensColors.TextHi,
            fontSize = 16.sp,
            style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp),
        )

        Text(
            text = "Log out",
            color = OpenLensColors.TextLo,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp, end = 20.dp)
                .clickable(onClick = onLogout),
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
            ModeStrip(
                selected = selectedMode,
                enabled = !busy,
                onSelect = onModeSelected,
                modifier = Modifier.padding(bottom = 18.dp),
            )
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
                            if (bytes != null) {
                                review(bytes, canRetake = true)
                            } else {
                                capturing = false
                                error = "Couldn't capture — try again"
                            }
                        }
                    },
                )
            }
        }

        picked?.let { bytes ->
            GalleryReveal(
                bytes = bytes,
                origin = galleryOrigin,
                // A picked image can't be re-shot, so a blurry one goes straight to the manual prompt.
                onFinished = { review(bytes, canRetake = false) },
            )
        }

        // Over-the-frame soft gate; the offending image stays visible behind it.
        blurryBytes?.let { bytes ->
            BlurWarning(
                bytes = bytes,
                onRetake = { blurryBytes = null },
                onUseAnyway = {
                    blurryBytes = null
                    onCaptured(bytes)
                },
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

/**
 * Camera-app style tier picker: a centered row of mode labels, the active one enlarged and in the
 * near-white accent. Tap a label to jump to it, or drag horizontally and release past a threshold to
 * step one mode over — both settle with the same spring that re-centers the selected slot. [enabled]
 * dims and freezes the strip while a capture is in flight.
 */
@Composable
private fun ModeStrip(
    selected: ScanMode,
    enabled: Boolean,
    onSelect: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = ScanMode.entries
    val selectedIndex = modes.indexOf(selected)
    val middleIndex = (modes.size - 1) / 2f

    val density = LocalDensity.current
    val slotWidth = 104.dp
    val slotWidthPx = with(density) { slotWidth.toPx() }
    // Releasing past a third of a slot from the current centre commits to the neighbouring mode.
    val threshold = slotWidthPx * 0.33f

    // Row translation that centres slot [i]: the middle slot sits at 0, each step is one slot wide.
    fun offsetForIndex(i: Int) = (middleIndex - i) * slotWidthPx

    // ONE plain-float source of truth for the row position. It is written *synchronously* during the
    // drag (allocation-free — no coroutine per touch frame) and read in the draw phase by
    // graphicsLayer. On release a settle animation eases this same value from wherever the finger let
    // go straight to the target centre, so it never jumps back to the old centre first.
    var offsetX by remember { mutableFloatStateOf(offsetForIndex(selectedIndex)) }
    val settleSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // Settle runs one coroutine per release (not per frame); the Animatable drives [offsetX] back.
    fun settleTo(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            Animatable(offsetX).animateTo(target, settleSpec) { offsetX = this.value }
        }
    }

    // Taps (and any external selection change) glide to the new centre — but never mid-drag, where
    // the finger owns the position.
    LaunchedEffect(selectedIndex) {
        if (!dragging) settleTo(offsetForIndex(selectedIndex))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .pointerInput(enabled, selectedIndex) {
                if (!enabled) return@pointerInput
                // A little overscroll past the ends so they don't feel like a hard wall.
                val minOffset = offsetForIndex(modes.lastIndex) - slotWidthPx * 0.4f
                val maxOffset = offsetForIndex(0) + slotWidthPx * 0.4f
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        settleJob?.cancel() // hand control to the finger, even mid-settle
                    },
                    onDragEnd = {
                        dragging = false
                        val dragged = offsetX - offsetForIndex(selectedIndex)
                        val steps = when {
                            dragged <= -threshold -> 1   // dragged left -> heavier mode
                            dragged >= threshold -> -1   // dragged right -> lighter mode
                            else -> 0
                        }
                        val next = (selectedIndex + steps).coerceIn(0, modes.lastIndex)
                        if (next != selectedIndex) {
                            // Selection change re-fires the LaunchedEffect, which settles from here.
                            onSelect(modes[next])
                        } else {
                            settleTo(offsetForIndex(selectedIndex))
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        settleTo(offsetForIndex(selectedIndex))
                    },
                ) { change, dragAmount ->
                    change.consume()
                    // Synchronous, allocation-free position update — the fix for per-frame GC churn.
                    offsetX = (offsetX + dragAmount).coerceIn(minOffset, maxOffset)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // graphicsLayer reads offsetX in the draw phase only — no relayout per touch frame.
        Row(modifier = Modifier.graphicsLayer { translationX = offsetX }) {
            modes.forEach { mode ->
                ModeLabel(
                    mode = mode,
                    active = mode == selected,
                    enabled = enabled,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.width(slotWidth),
                )
            }
        }
    }
}

/** One label slot in [ModeStrip]: color/size lift when active, with a small accent dot beneath. */
@Composable
private fun ModeLabel(
    mode: ScanMode,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (active) OpenLensColors.TextHi else OpenLensColors.TextLo,
        label = "modeLabelColor",
    )
    val dotAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        label = "modeDotAlpha",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = mode.label,
            color = color,
            fontSize = if (active) 16.sp else 14.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 10f)),
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(4.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(OpenLensColors.Accent),
        )
    }
}

/**
 * Full-screen soft gate shown when a shot scores below [BlurCheck.THRESHOLD]. The offending frame
 * sits full-bleed under a scrim so the user can see what was blurry; [onRetake] dismisses back to the
 * camera, [onUseAnyway] sends the shot on regardless. Deliberately non-blocking — a false positive on
 * a genuinely sharp but low-texture subject costs one tap, never a trapped capture.
 */
@Composable
private fun BlurWarning(bytes: ByteArray, onRetake: () -> Unit, onUseAnyway: () -> Unit) {
    val image = remember(bytes) { decodeImageBitmap(bytes) }

    Box(modifier = Modifier.fillMaxSize().background(OpenLensColors.Bg)) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Darken the frame so the message and buttons stay legible over any scene.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Too blurry",
                color = OpenLensColors.TextHi,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "This shot looks out of focus. Retake it for a sharper read.",
                color = OpenLensColors.TextLo,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Primary: get a better shot.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OpenLensColors.Accent)
                    .clickable(onClick = onRetake)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Retake",
                    color = OpenLensColors.OnAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            // Secondary: proceed with the blurry shot anyway.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onUseAnyway)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Use anyway", color = OpenLensColors.TextLo, fontSize = 15.sp)
            }
        }
    }
}
