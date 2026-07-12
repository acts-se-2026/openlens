package com.openlens.app.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual class CameraController {
    internal val session = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private var delegate: PhotoCaptureDelegate? = null
    private var configured = false

    /** Add camera input + photo output and start the session (off the main thread). */
    internal fun configure() {
        if (configured) return
        configured = true
        dispatch_async(dispatch_get_global_queue(0L, 0uL)) {
            session.beginConfiguration()
            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            if (device != null) {
                val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
                if (input != null && session.canAddInput(input)) session.addInput(input)
            }
            if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)
            session.commitConfiguration()
            session.startRunning()
        }
    }

    /** Stop the capture session (off the main thread) so the camera is released when we leave. */
    internal fun stopSession() {
        dispatch_async(dispatch_get_global_queue(0L, 0uL)) {
            if (session.running) session.stopRunning()
        }
    }

    actual fun takePicture(onResult: (ByteArray?) -> Unit) {
        if (!session.running) {
            onResult(null)
            return
        }
        val d = PhotoCaptureDelegate { bytes ->
            delegate = null // release once fired
            onResult(bytes)
        }
        delegate = d // hold a strong ref (AVFoundation keeps it weakly)
        photoOutput.capturePhotoWithSettings(AVCapturePhotoSettings(), d)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoCaptureDelegate(
    private val onData: (ByteArray?) -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        onData(didFinishProcessingPhoto.fileDataRepresentation()?.toByteArray())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) {
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
    return out
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCameraController(): CameraController = remember { CameraController() }

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(controller: CameraController, modifier: Modifier) {
    val previewLayer = remember {
        AVCaptureVideoPreviewLayer(session = controller.session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }

    LaunchedEffect(Unit) {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            if (granted) controller.configure()
        }
    }

    // Stop the camera when the capture screen leaves composition.
    DisposableEffect(Unit) {
        onDispose { controller.stopSession() }
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
        },
    )
}
