package com.openlens.app.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Owns the platform capture pipeline (CameraX ImageCapture / AVCapturePhotoOutput).
 * [CameraPreview] binds the live preview to it; [takePicture] grabs a still frame as encoded bytes.
 */
expect class CameraController {
    fun takePicture(onResult: (ByteArray?) -> Unit)
}

@Composable
expect fun rememberCameraController(): CameraController

@Composable
expect fun CameraPreview(controller: CameraController, modifier: Modifier = Modifier)
