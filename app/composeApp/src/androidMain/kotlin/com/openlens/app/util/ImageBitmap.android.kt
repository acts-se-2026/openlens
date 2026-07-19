package com.openlens.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayInputStream

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    // CameraX JPEGs carry orientation in EXIF rather than rotated pixels; apply it so the
    // result isn't sideways.
    val degrees = when (readExifOrientation(bytes)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return bitmap.asImageBitmap()

    val rotated = Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { postRotate(degrees) }, true,
    )
    return rotated.asImageBitmap()
}

actual fun decodeImageBitmapSampled(bytes: ByteArray, maxDimensionPx: Int): ImageBitmap? = try {
    // First pass reads only the dimensions (no pixels allocated) to pick a power-of-two downsample.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)

    val options = BitmapFactory.Options().apply {
        var sample = 1
        while (longEdge > 0 && longEdge / (sample * 2) >= maxDimensionPx) sample *= 2
        inSampleSize = sample
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
} catch (_: Throwable) {
    // Throwable, not Exception: a full-res decode can throw OutOfMemoryError, which we must not crash on.
    null
}

private fun readExifOrientation(bytes: ByteArray): Int = try {
    ByteArrayInputStream(bytes).use { stream ->
        ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }
} catch (_: Exception) {
    ExifInterface.ORIENTATION_NORMAL
}
