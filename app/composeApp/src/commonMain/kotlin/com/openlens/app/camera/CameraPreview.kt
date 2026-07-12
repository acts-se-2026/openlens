package com.openlens.app.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Live camera preview, implemented per-platform (CameraX on Android, AVFoundation on iOS)
 * and composed under the shared Compose UI. Spike: prove the interop renders on both platforms.
 */
@Composable
expect fun CameraPreview(modifier: Modifier = Modifier)
