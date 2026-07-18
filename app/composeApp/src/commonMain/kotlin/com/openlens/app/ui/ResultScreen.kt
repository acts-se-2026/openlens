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
import com.openlens.app.scan.DetectedBox
import com.openlens.app.scan.Env
import com.openlens.app.scan.NormRect
import com.openlens.app.scan.RelatedImage
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
    detect: suspend (ByteArray) -> List<DetectedBox>,
    searchImages: suspend (String) -> List<RelatedImage>,
    loadImage: suspend (String) -> ByteArray?,
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
    // Detected objects (image 0..1 coords), drawn as tappable suggestions. Empty on servers without YOLO.
    var detections by remember { mutableStateOf<List<NormRect>>(emptyList()) }
    // Similar-images strip: null until the detail sheet is first opened. Lazy on purpose — we only
    // search when the user actually asks to see it. [searchedQuery] tracks the heading we last searched
    // so it refreshes if a region re-identify changes the answer, but not on every reopen.
    var similarState by remember { mutableStateOf<SimilarImagesState?>(null) }
    var searchedQuery by remember { mutableStateOf<String?>(null) }
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

    // Detection runs once, independent of the scan; empty results just mean no suggestions.
    LaunchedEffect(Unit) {
        val found = runCatching { detect(bytes) }.getOrDefault(emptyList()).map { it.rect }
        // Fallback only in the dev build, so a prod server that legitimately finds nothing shows nothing.
        detections = found.ifEmpty { if (Env.ACTIVE == Env.Dev) DEV_FALLBACK_DETECTIONS else emptyList() }
    }

    // Search for similar images when the detail sheet opens, using the current answer's heading as the
    // query. Re-runs if the heading changes (region re-identify) but not on a plain reopen. The search
    // never throws (empty = "none found"); a superseded query cancels cleanly when the key changes.
    val currentLabel = result?.label
    LaunchedEffect(showDetail, currentLabel) {
        if (showDetail && !currentLabel.isNullOrBlank() && currentLabel != searchedQuery) {
            similarState = SimilarImagesState.Loading
            val images = runCatching { searchImages(currentLabel) }.getOrDefault(emptyList())
            similarState = SimilarImagesState.Ready(images)
            // Mark done only after the search actually completes — so if the sheet is closed mid-search
            // (cancelling this effect before Ready is set), reopening re-runs it instead of getting
            // stuck on the Loading spinner forever.
            searchedQuery = currentLabel
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
        val detectionsScreen = detections.map {
            imageRectToOverlay(it, containerSize.width.toFloat(), containerSize.height.toFloat(), image.width, image.height)
        }
        EditableFrameOverlay(
            rect = rect,
            onRectChange = { rect = it },
            detections = detectionsScreen,
            // Snap the frame to the tapped object; the user chooses when to Identify.
            onDetectionTap = { snap -> rect = snap },
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
                    similarState = similarState ?: SimilarImagesState.Loading,
                    loadImage = loadImage,
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

/** Inverse of [overlayToImageRegion]: image 0..1 coords -> overlay 0..1 coords (to draw detections). */
private fun imageRectToOverlay(rect: NormRect, cw: Float, ch: Float, iw: Int, ih: Int): NormRect {
    if (cw <= 0f || ch <= 0f || iw <= 0 || ih <= 0) return rect
    val scale = maxOf(cw / iw, ch / ih)
    val dw = iw * scale
    val dh = ih * scale
    val ox = (cw - dw) / 2f
    val oy = (ch - dh) / 2f
    fun toCx(x: Float) = (ox + x * dw) / cw
    fun toCy(y: Float) = (oy + y * dh) / ch
    return NormRect(toCx(rect.left), toCy(rect.top), toCx(rect.right), toCy(rect.bottom))
}

// DEV-ONLY canned detections (gated to Env.Dev) so the tappable-suggestion UX is visible on a server
// without YOLO — this dev machine can't run it. Real /detect boxes replace these automatically.
private val DEV_FALLBACK_DETECTIONS = listOf(
    NormRect(0.14f, 0.20f, 0.52f, 0.72f),
    NormRect(0.55f, 0.34f, 0.86f, 0.74f),
)
