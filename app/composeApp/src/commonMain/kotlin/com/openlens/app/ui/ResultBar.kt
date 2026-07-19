package com.openlens.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.scan.ScanResult
import com.openlens.app.ui.theme.OpenLensColors
import kotlin.math.abs

/**
 * Floating pill (caller sizes it to ~3/4 width). Two calm zones: the answer line — tap anywhere on it
 * to open the reading card (a quiet chevron hints at it) — and the single, clearly-labeled
 * "Identify this frame" button (spinner while [analyzing]). Retake lives elsewhere ([RetakeButton]).
 */
@Composable
fun ResultPill(
    result: ScanResult?,
    analyzing: Boolean,
    onIdentify: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = OpenLensColors.Surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = result != null, onClick = onOpenDetail)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result?.label ?: if (analyzing) "Identifying…" else "Frame the subject",
                    color = OpenLensColors.TextHi,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (result != null) {
                    ChevronDown(OpenLensColors.TextLo, modifier = Modifier.padding(start = 10.dp))
                }
            }

            // The 1-tap action: re-analyze the current frame.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(OpenLensColors.Accent.copy(alpha = if (analyzing) 0.6f else 1f))
                    .clickable(enabled = !analyzing, onClick = onIdentify)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (analyzing) {
                    CircularProgressIndicator(
                        color = OpenLensColors.OnAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReticleIcon(OpenLensColors.OnAccent)
                        Text(
                            text = "Identify this frame",
                            color = OpenLensColors.OnAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Labeled "Retake" control for a top corner: a camera glyph + text on a dark scrim, so it's clearly
 * a "new photo" action rather than a bare, guessable icon. */
@Composable
fun RetakeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraIcon()
        Text(
            text = "Retake",
            color = OpenLensColors.TextHi,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

/**
 * Full reading card, presented as a draggable bottom sheet. It opens at a "peek" height ([PEEK_FRACTION]
 * of the screen) showing the heading and the top of the description; dragging the grab handle up — or
 * simply swiping up on the body — grows it toward [EXPANDED_FRACTION] and reveals the similar-images
 * masonry, after which the body scrolls normally. A downward swipe/drag collapses it, and past a small
 * threshold dismisses it. The scrim + slide-in/out are owned by the caller.
 */
@Composable
fun ResultDetailCard(
    result: ScanResult,
    similarState: SimilarImagesState,
    loadImage: suspend (String) -> ByteArray?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val peekPx = with(density) { (maxHeight * PEEK_FRACTION).toPx() }
        val expandedPx = with(density) { (maxHeight * EXPANDED_FRACTION).toPx() }
        val flingThresholdPx = with(density) { 420.dp.toPx() }
        // Live sheet height in px; the grab handle and the body's nested scroll both write it, and it
        // animates to the nearer anchor (peek / expanded) when a gesture ends.
        val heightPx = remember { mutableFloatStateOf(peekPx) }
        val handler = remember(peekPx, expandedPx) {
            SheetDragHandler(peekPx, expandedPx, heightPx, flingThresholdPx)
        }
        handler.onClose = onClose
        val heightDp = with(density) { heightPx.floatValue.toDp() }

        Surface(
            color = OpenLensColors.Surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                // Swallow taps on the card so they don't fall through to the dismiss scrim.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Grab handle — draggable to grow/shrink the sheet directly.
                DragHandle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta -> handler.onHandleDrag(delta) },
                            onDragStopped = { velocity -> handler.onHandleDragStopped(velocity) },
                        ),
                )
                // Body: swiping up here first expands the sheet (via [handler]'s nested scroll), then
                // scrolls the content once the sheet is fully open.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(handler)
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 24.dp, top = 2.dp, end = 14.dp, bottom = 22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = result.label,
                            color = OpenLensColors.TextHi,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onClose)
                                .padding(10.dp),
                        ) {
                            Text("✕", color = OpenLensColors.TextLo, fontSize = 18.sp)
                        }
                    }
                    Text(
                        text = result.detail,
                        color = OpenLensColors.TextLo,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 12.dp, end = 10.dp),
                    )
                    SimilarImagesSection(
                        state = similarState,
                        loadImage = loadImage,
                        modifier = Modifier.padding(top = 20.dp, end = 10.dp),
                    )
                }
            }
        }
    }
}

/** The little rounded "grabber" at the top of the sheet — the drag affordance (and drag surface). */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(OpenLensColors.TextLo.copy(alpha = 0.4f)),
        )
    }
}

/** A small camera glyph. */
@Composable
private fun CameraIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = OpenLensColors.TextHi,
            topLeft = Offset(w * 0.30f, h * 0.18f),
            size = Size(w * 0.24f, h * 0.16f),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = stroke,
        )
        drawRoundRect(
            color = OpenLensColors.TextHi,
            topLeft = Offset(w * 0.06f, h * 0.30f),
            size = Size(w * 0.88f, h * 0.52f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        drawCircle(
            color = OpenLensColors.TextHi,
            radius = w * 0.15f,
            center = Offset(w * 0.5f, h * 0.58f),
            style = stroke,
        )
    }
}

/** A small viewfinder reticle (four corner brackets) — reinforces "scan/identify the framed area". */
@Composable
private fun ReticleIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val arm = w * 0.34f
        val s = 1.8.dp.toPx()
        val cap = StrokeCap.Round
        drawLine(color, Offset(0f, arm), Offset(0f, 0f), s, cap)
        drawLine(color, Offset(0f, 0f), Offset(arm, 0f), s, cap)
        drawLine(color, Offset(w - arm, 0f), Offset(w, 0f), s, cap)
        drawLine(color, Offset(w, 0f), Offset(w, arm), s, cap)
        drawLine(color, Offset(0f, h - arm), Offset(0f, h), s, cap)
        drawLine(color, Offset(0f, h), Offset(arm, h), s, cap)
        drawLine(color, Offset(w - arm, h), Offset(w, h), s, cap)
        drawLine(color, Offset(w, h - arm), Offset(w, h), s, cap)
        drawCircle(color, radius = 1.2.dp.toPx(), center = Offset(w / 2f, h / 2f))
    }
}

/** A small downward chevron — the quiet "tap for details" cue on the answer line. */
@Composable
private fun ChevronDown(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 14.dp, height = 9.dp)) {
        val s = 1.8.dp.toPx()
        val midX = size.width / 2f
        drawLine(color, Offset(size.width * 0.12f, size.height * 0.28f), Offset(midX, size.height * 0.78f), s, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.88f, size.height * 0.28f), Offset(midX, size.height * 0.78f), s, StrokeCap.Round)
    }
}

/**
 * Sizes the [ResultDetailCard] between its "peek" and "expanded" anchors. It doubles as the body's
 * [NestedScrollConnection] (swipe up grows the sheet before the content scrolls; swipe down at the top
 * shrinks it) and backs the grab handle's drag. When a gesture ends the sheet settles to the nearer
 * anchor — or, if it's been pulled down far enough, closes via [onClose].
 */
private class SheetDragHandler(
    private val peekPx: Float,
    private val expandedPx: Float,
    private val height: MutableFloatState,
    private val flingThresholdPx: Float,
) : NestedScrollConnection {
    // Reassigned each recomposition so a stale close callback is never held.
    var onClose: () -> Unit = {}

    // Released below [dismissPx] ⇒ dismiss; [minPx] is the hard floor a downward drag can reach.
    private val dismissPx = peekPx * 0.55f
    private val minPx = peekPx * 0.35f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        // Finger up (dy < 0) and not fully open: grow the sheet, consuming that much of the scroll.
        if (dy < 0f && height.floatValue < expandedPx) {
            val target = (height.floatValue - dy).coerceAtMost(expandedPx)
            val consumed = target - height.floatValue
            height.floatValue = target
            return Offset(0f, -consumed)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        // Finger down with scroll left over (content already at its top): shrink the sheet.
        if (dy > 0f && height.floatValue > minPx) {
            val target = (height.floatValue - dy).coerceAtLeast(minPx)
            val used = height.floatValue - target
            height.floatValue = target
            return Offset(0f, used)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // A fling begun while the sheet was mid-drag settles the sheet; once fully expanded the fling
        // belongs to the content instead.
        if (height.floatValue < expandedPx) {
            settle(available.y)
            return available
        }
        return Velocity.Zero
    }

    fun onHandleDrag(delta: Float) {
        height.floatValue = (height.floatValue - delta).coerceIn(minPx, expandedPx)
    }

    suspend fun onHandleDragStopped(velocity: Float) = settle(velocity)

    private suspend fun settle(velocity: Float) {
        val mid = (peekPx + expandedPx) / 2f
        val strong = abs(velocity) > flingThresholdPx
        // velocity > 0 is a downward gesture, < 0 upward.
        val dismiss = when {
            strong && velocity > 0f -> height.floatValue <= mid
            strong && velocity < 0f -> false
            else -> height.floatValue < dismissPx
        }
        if (dismiss) {
            onClose()
            return
        }
        val target = when {
            strong && velocity < 0f -> expandedPx
            strong && velocity > 0f -> peekPx
            height.floatValue < mid -> peekPx
            else -> expandedPx
        }
        animate(height.floatValue, target, initialVelocity = velocity) { value, _ ->
            height.floatValue = value
        }
    }
}

// The sheet opens at ~this fraction of the screen (a comfortable third-and-a-bit) and grows to this
// fraction when the user drags it up.
private const val PEEK_FRACTION = 0.4f
private const val EXPANDED_FRACTION = 0.9f
