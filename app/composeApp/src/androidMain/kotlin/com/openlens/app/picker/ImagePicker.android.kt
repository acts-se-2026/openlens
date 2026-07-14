package com.openlens.app.picker

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual class ImagePickerLauncher internal constructor(private val onLaunch: () -> Unit) {
    actual fun launch() = onLaunch()
}

@Composable
actual fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): ImagePickerLauncher {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // A null uri means the picker was dismissed without a choice; stay silent.
        if (uri != null) {
            scope.launch {
                val jpeg = withContext(Dispatchers.IO) { readAsUprightJpeg(context, uri) }
                currentOnResult(jpeg)
            }
        }
    }

    return remember(launcher) {
        ImagePickerLauncher {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

// Gallery images arrive in arbitrary formats (HEIC, PNG, WebP…) and sizes. The backend only
// accepts JPEG and huge bitmaps risk OOM, so decode downsampled, rotate upright, re-encode.
private const val MAX_DIMENSION = 2560
private const val JPEG_QUALITY = 90

private fun readAsUprightJpeg(context: Context, uri: Uri): ByteArray? = try {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight))
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        bitmap?.let { encodeJpeg(rotateUpright(resolver, uri, it)) }
    }
} catch (_: Exception) {
    null
}

/** Largest power-of-two shrink that keeps the longest side at or above [MAX_DIMENSION]. */
private fun sampleSizeFor(longestSide: Int): Int {
    var sampleSize = 1
    while (longestSide / (sampleSize * 2) >= MAX_DIMENSION) sampleSize *= 2
    return sampleSize
}

/** Re-encoding drops the EXIF block, so any orientation must be baked into the pixels first. */
private fun rotateUpright(resolver: ContentResolver, uri: Uri, bitmap: Bitmap): Bitmap {
    val degrees = try {
        resolver.openInputStream(uri)?.use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (_: Exception) {
        0f
    }
    if (degrees == 0f) return bitmap
    return Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { postRotate(degrees) }, true,
    )
}

private fun encodeJpeg(bitmap: Bitmap): ByteArray? {
    val out = ByteArrayOutputStream()
    return if (bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) out.toByteArray() else null
}
