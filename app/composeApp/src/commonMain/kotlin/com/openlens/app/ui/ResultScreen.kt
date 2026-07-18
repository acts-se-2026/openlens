package com.openlens.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.openlens.app.scan.NormRect
import com.openlens.app.scan.ScanResult
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.util.decodeImageBitmap
import kotlinx.coroutines.launch

/**
 * Full-bleed result screen: the frozen photo fills the screen ([ContentScale.Crop]) with an editable
 * frame over it and the answer in a sheet below. Move/resize the frame and hit "Identify this frame"
 * to re-run analysis on the new region — the sheet updates in place. Runs an initial scan once the
 * screen is measured. [identify] does the network call + error handling; [onScanAgain] → camera.
 */
@Composable
fun ResultScreen(
    bytes: ByteArray,
    identify: suspend (NormRect) -> ScanResult,
    onScanAgain: () -> Unit,
) {
    val image = remember(bytes) { decodeImageBitmap(bytes) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // Frame in overlay-normalized coords (0..1 of the screen), so it always stays on the full-bleed photo.
    var rect by remember { mutableStateOf(NormRect(0.05f, 0.05f, 0.95f, 0.95f)) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var didInitialScan by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun scan() {
        if (analyzing) return
        val cs = containerSize
        val region = overlayToImageRegion(rect, cs.width.toFloat(), cs.height.toFloat(), image.width, image.height)
        scope.launch {
            analyzing = true
            result = identify(region)
            analyzing = false
        }
    }

    // First scan once the screen is measured (the region math needs the container size).
    LaunchedEffect(containerSize) {
        if (!didInitialScan && containerSize.width > 0 && containerSize.height > 0) {
            didInitialScan = true
            scan()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OpenLensColors.Bg)
            .onSizeChanged { containerSize = it },
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        EditableFrameOverlay(
            rect = rect,
            onRectChange = { rect = it },
            modifier = Modifier.fillMaxSize(),
        )

        // Retake sits up in the corner, away from the bottom action zone.
        RetakeButton(
            onClick = onScanAgain,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
        )

        var barVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { barVisible = true }
        AnimatedVisibility(
            visible = barVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            ResultPill(
                result = result,
                analyzing = analyzing,
                onIdentify = { scan() },
                onOpenDetail = { showDetail = true },
                modifier = Modifier.fillMaxWidth(0.75f),
            )
        }

        // Detail overlay: the dim scrim fades in, the card slides up from the bottom.
        AnimatedVisibility(visible = showDetail, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showDetail = false },
                    ),
            )
        }
        val shown = result
        AnimatedVisibility(
            visible = showDetail && shown != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            shown?.let {
                ResultDetailCard(
                    result = it,
                    onClose = { showDetail = false },
                )
            }
        }
    }
}

/**
 * Map a rectangle normalized to the on-screen overlay ([rect], 0..1 of the container) into the image's
 * own 0..1 coordinates under [ContentScale.Crop] (the photo fills the screen and overflows, so only
 * its centre is visible). The result is the region the backend crops to.
 */
private fun overlayToImageRegion(rect: NormRect, cw: Float, ch: Float, iw: Int, ih: Int): NormRect {
    if (cw <= 0f || ch <= 0f || iw <= 0 || ih <= 0) return rect
    val scale = maxOf(cw / iw, ch / ih)
    val dw = iw * scale
    val dh = ih * scale
    val ox = (cw - dw) / 2f
    val oy = (ch - dh) / 2f
    fun toNx(x: Float) = ((x * cw - ox) / dw).coerceIn(0f, 1f)
    fun toNy(y: Float) = ((y * ch - oy) / dh).coerceIn(0f, 1f)
    return NormRect(toNx(rect.left), toNy(rect.top), toNx(rect.right), toNy(rect.bottom))
}
