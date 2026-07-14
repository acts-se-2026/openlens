package com.openlens.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.util.decodeImageBitmap

/**
 * In-place scanning state: the just-captured frame stays on screen (no cut to black) with a
 * pulsing neutral reticle breathing over its center while the model thinks.
 *
 * Structured as a stack of layers — frozen photo, scrim, reticle — so a detection **bounding-box**
 * layer can later drop in as another sibling over the same image.
 */
@Composable
fun ScanningScreen(bytes: ByteArray) {
    val image = remember(bytes) { decodeImageBitmap(bytes) }

    // A single "breathe" value drives both the reticle's scale and its opacity.
    val transition = rememberInfiniteTransition(label = "reticle")
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val reticleScale = 0.9f + 0.1f * breathe
    val reticleAlpha = 0.45f + 0.55f * breathe

    Box(modifier = Modifier.fillMaxSize().background(OpenLensColors.Bg)) {
        // Frozen captured frame.
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Gentle scrim so the reticle stays legible over any photo.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))

        // Reticle + caption, centered.
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Reticle(
                modifier = Modifier
                    .size(200.dp)
                    .scale(reticleScale)
                    .alpha(reticleAlpha),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Identifying…",
                color = OpenLensColors.TextLo,
                fontSize = 14.sp,
            )
        }
    }
}

/** Four corner brackets framing a small center dot — a camera-native focus reticle. */
@Composable
private fun Reticle(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val accent = OpenLensColors.Accent
        val stroke = 2.5.dp.toPx()
        val arm = 26.dp.toPx()
        val cap = StrokeCap.Round
        val w = size.width
        val h = size.height

        // Corner brackets (each an L of two strokes).
        drawLine(accent, Offset(0f, 0f), Offset(arm, 0f), stroke, cap)
        drawLine(accent, Offset(0f, 0f), Offset(0f, arm), stroke, cap)
        drawLine(accent, Offset(w, 0f), Offset(w - arm, 0f), stroke, cap)
        drawLine(accent, Offset(w, 0f), Offset(w, arm), stroke, cap)
        drawLine(accent, Offset(0f, h), Offset(arm, h), stroke, cap)
        drawLine(accent, Offset(0f, h), Offset(0f, h - arm), stroke, cap)
        drawLine(accent, Offset(w, h), Offset(w - arm, h), stroke, cap)
        drawLine(accent, Offset(w, h), Offset(w, h - arm), stroke, cap)

        // Center dot.
        drawCircle(accent, radius = 3.dp.toPx(), center = Offset(w / 2f, h / 2f))
    }
}
