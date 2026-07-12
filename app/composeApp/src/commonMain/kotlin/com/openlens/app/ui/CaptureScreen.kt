package com.openlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.camera.CameraPreview
import com.openlens.app.ui.theme.OpenLensColors

/** Home: full-bleed camera with a cyan shutter. No mode chips (lean MVP). */
@Composable
fun CaptureScreen(onCapture: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(OpenLensColors.Bg)) {
        CameraPreview(Modifier.fillMaxSize())

        // Top chrome — quiet wordmark, respects the status bar inset.
        Text(
            text = "OpenLens",
            color = OpenLensColors.TextHi,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp),
        )

        // Bottom chrome — shutter, respects the navigation-bar / home-indicator inset.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShutterButton(onClick = onCapture)
        }
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    // Cyan ring with a solid inner disc — classic shutter, one-accent.
    Box(
        modifier = Modifier
            .size(74.dp)
            .border(width = 3.dp, color = OpenLensColors.Accent, shape = CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(OpenLensColors.Accent)
            .clickable(onClick = onClick),
    )
}
