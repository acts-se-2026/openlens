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
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShutterButton(
                onClick = { controller.takePicture { bytes -> if (bytes != null) onCaptured(bytes) } },
            )
        }
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
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
