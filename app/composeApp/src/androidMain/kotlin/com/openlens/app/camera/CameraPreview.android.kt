package com.openlens.app.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

actual class CameraController {
    internal var imageCapture: ImageCapture? = null
    internal var executor: Executor? = null
    internal var cameraProvider: ProcessCameraProvider? = null

    actual fun takePicture(onResult: (ByteArray?) -> Unit) {
        val capture = imageCapture
        val exec = executor
        if (capture == null || exec == null) {
            onResult(null)
            return
        }
        capture.takePicture(exec, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer = image.planes[0].buffer // JPEG output → single plane
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val rotationDegrees = image.imageInfo.rotationDegrees
                image.close()
                // The raw camera JPEG is multiple MB; the backend downscales to 1600px anyway, so
                // uploading full-res just wastes mobile uplink (the bulk of scan latency). Shrink +
                // re-encode here, falling back to the original bytes if anything goes wrong.
                onResult(downscaleForUpload(bytes, rotationDegrees) ?: bytes)
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(null)
            }
        })
    }
}

private const val MAX_UPLOAD_DIMENSION = 1600
private const val UPLOAD_JPEG_QUALITY = 85

/**
 * Shrink a full-res camera JPEG to at most [MAX_UPLOAD_DIMENSION] on its long edge and re-encode at
 * [UPLOAD_JPEG_QUALITY], baking [rotationDegrees] into the pixels (the re-encoded JPEG carries no EXIF
 * orientation, so it's already upright for the server). The backend downscales to 1600px regardless,
 * so this only removes wasted upload bytes — multi-MB → a few hundred KB, the bulk of scan latency on
 * mobile. Returns null on any failure so the caller falls back to the original bytes.
 */
private fun downscaleForUpload(jpeg: ByteArray, rotationDegrees: Int): ByteArray? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)

    // Coarse power-of-two downsample at decode time (cheap + memory-safe), then exact scale below.
    val decodeOpts = BitmapFactory.Options().apply {
        var sample = 1
        while (longEdge > 0 && longEdge / (sample * 2) >= MAX_UPLOAD_DIMENSION) sample *= 2
        inSampleSize = sample
    }
    val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, decodeOpts) ?: return null

    val scale = MAX_UPLOAD_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height)
    val matrix = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
    }
    val scaled =
        if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (scaled != decoded) decoded.recycle()

    ByteArrayOutputStream().use { stream ->
        scaled.compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, stream)
        scaled.recycle()
        stream.toByteArray()
    }
} catch (_: Throwable) {
    null
}

@Composable
actual fun rememberCameraController(): CameraController = remember { CameraController() }

@Composable
actual fun CameraPreview(controller: CameraController, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    // Release the camera when the capture screen leaves composition, so it isn't left running
    // in the background while scanning / viewing the result.
    DisposableEffect(Unit) {
        onDispose { controller.cameraProvider?.unbindAll() }
    }

    Box(modifier) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        controller.imageCapture = imageCapture
                        controller.executor = ContextCompat.getMainExecutor(ctx)
                        controller.cameraProvider = provider
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                            )
                        } catch (_: Exception) {
                            // spike: swallow; a real impl surfaces this to the UI
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }
    }
}
