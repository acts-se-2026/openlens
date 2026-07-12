package com.openlens.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.camera.CameraPreview
import com.openlens.app.camera.rememberCameraController
import com.openlens.app.ui.theme.OpenLensColors

/** Home: full-bleed camera with a cyan shutter. Tapping it captures a frame. */
@Composable
fun CaptureScreen(onCaptured: (ByteArray) -> Unit) {
    val controller = rememberCameraController()

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
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShutterButton(
                onClick = { controller.takePicture { bytes -> if (bytes != null) onCaptured(bytes) } },
            )
        }
    }
}

/**
 * Cyan shutter that reacts to the tap: it scales down while pressed (springing back on release),
 * and fires a one-shot ring "ping" outward on each capture as confirmation.
 */
@Composable
private fun ShutterButton(onClick: () -> Unit) {
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
                    color = OpenLensColors.Accent,
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
                .border(width = 3.dp, color = OpenLensColors.Accent, shape = CircleShape)
                .padding(7.dp)
                .clip(CircleShape)
                .background(OpenLensColors.Accent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        pingKey++
                        onClick()
                    },
                ),
        )
    }
}
