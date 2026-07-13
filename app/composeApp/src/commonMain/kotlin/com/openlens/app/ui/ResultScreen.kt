package com.openlens.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.scan.ScanResult
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.util.decodeImageBitmap

/**
 * Single best answer over the captured frame. The photo stays full-bleed (same size it had while
 * scanning — no resize/jump), and the answer slides up as a bottom sheet over it.
 */
@Composable
fun ResultScreen(bytes: ByteArray, result: ScanResult, onScanAgain: () -> Unit) {
    val image = remember(bytes) { decodeImageBitmap(bytes) }

    Box(modifier = Modifier.fillMaxSize().background(OpenLensColors.Bg)) {
        // Full-bleed frozen frame — identical framing to the scanning state, so nothing jumps.
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Animate the sheet up once, on first composition.
        var sheetVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { sheetVisible = true }

        AnimatedVisibility(
            visible = sheetVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ResultSheet(result = result, onScanAgain = onScanAgain)
        }
    }
}

/**
 * Rounded-top bottom sheet holding the answer, floating over the photo.
 *
 * Collapsed by default: heading + ~2 lines of the description. Drag the sheet up (or tap it) to
 * expand and reveal the full description — the sheet grows upward as the text unfolds, and drags
 * back down to collapse.
 */
@Composable
private fun ResultSheet(result: ScanResult, onScanAgain: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Latches true once the collapsed text is found to overflow 2 lines, and stays true. That keeps
    // the hint mounted in BOTH states (it just rotates), so toggling never snaps the layout. Short
    // results never trip it, so no misleading arrow appears when there's nothing to expand.
    var expandable by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Floats just above the sheet, over the photo — the "pull up / push down" cue. Rides up as
        // the sheet grows and rotates to point down once expanded. Mounted whenever the text is long
        // enough to expand and kept mounted across the toggle, so it never snaps in or out.
        if (expandable) {
            ScrollHint(
                expanded = expanded,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp),
            )
        }

        Surface(
            color = OpenLensColors.Surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 16.dp)
                    // The sheet's height follows its content, so revealing more text slides its top up.
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
            ) {
                // Header is the grab target: drag up to expand, down to collapse; a tap toggles too.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { expanded = !expanded }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -6f) expanded = true
                                else if (dragAmount > 6f) expanded = false
                            }
                        },
                ) {
                    // Grabber handle — reads as a draggable sheet.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 16.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(OpenLensColors.TextLo.copy(alpha = 0.4f)),
                    )
                    Text(
                        text = result.label,
                        color = OpenLensColors.TextHi,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = result.detail,
                        color = OpenLensColors.TextLo,
                        fontSize = 15.sp,
                        // Collapsed: clip to two lines with an ellipsis. Expanded: show it all.
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        // Only the collapsed layout can tell us there's more; once known, it's latched.
                        onTextLayout = { layout ->
                            if (!expanded && layout.hasVisualOverflow) expandable = true
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TextButton(
                    onClick = onScanAgain,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text("Scan again", color = OpenLensColors.Accent, fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * A faint, slowly-bobbing chevron floating just above the sheet — a quiet "pull up for more" cue
 * that flips to point down once expanded ("push down to collapse"). Stays mounted across the toggle
 * so it only rotates, never appears/disappears. Accent cyan at low alpha, so it reads without shouting.
 */
@Composable
private fun ScrollHint(expanded: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scrollHint")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )
    // Up when collapsed, down when expanded — a smooth 180° flip on toggle.
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "hintRotation",
    )
    Canvas(modifier = modifier.size(width = 24.dp, height = 13.dp).rotate(rotation)) {
        val lift = 2.dp.toPx() * bob // gentle up/down drift
        val stroke = 2.dp.toPx()
        val midX = size.width / 2f
        val half = size.width * 0.30f
        val topY = size.height * 0.30f - lift
        val botY = size.height * 0.72f - lift
        drawLine(
            color = OpenLensColors.Accent,
            start = Offset(midX - half, botY),
            end = Offset(midX, topY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            alpha = 0.6f,
        )
        drawLine(
            color = OpenLensColors.Accent,
            start = Offset(midX, topY),
            end = Offset(midX + half, botY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            alpha = 0.6f,
        )
    }
}
