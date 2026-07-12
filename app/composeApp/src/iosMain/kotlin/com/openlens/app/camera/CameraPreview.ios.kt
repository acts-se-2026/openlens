package com.openlens.app.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(modifier: Modifier) {
    val session = remember { AVCaptureSession() }
    val previewLayer = remember {
        AVCaptureVideoPreviewLayer(session = session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }

    LaunchedEffect(Unit) {
        // requestAccessForMediaType is a companion extension (needs the import) taking
        // a (Boolean) -> Unit completion handler.
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            if (granted) configureAndStart(session)
        }
    }

    UIKitView(
        factory = {
            UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
                layer.addSublayer(previewLayer)
            }
        },
        modifier = modifier,
        onResize = { view, rect ->
            view.setFrame(rect)
            previewLayer.setFrame(rect)
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun configureAndStart(session: AVCaptureSession) {
    // Session setup + startRunning() must stay off the main thread (AVFoundation blocks).
    dispatch_async(dispatch_get_global_queue(0L, 0uL)) {
        session.beginConfiguration()
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device != null) {
            val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            if (input != null && session.canAddInput(input)) {
                session.addInput(input)
            }
        }
        session.commitConfiguration()
        session.startRunning()
    }
}
