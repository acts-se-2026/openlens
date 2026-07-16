package com.openlens.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decode downsampled (inSampleSize keeps us from ever allocating the full-resolution bitmap), finish
 * with an exact scale to the target long edge, then read luminance per pixel. Blur is
 * rotation-invariant, so EXIF orientation is deliberately ignored here.
 */
internal actual fun toGrayscaleDownscaled(bytes: ByteArray, longEdge: Int): GrayImage? {
    // First pass: bounds only, no pixel allocation.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(maxOf(srcW, srcH), longEdge)
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

    // inSampleSize only shrinks in powers of two, so finish with an exact scale down to the target.
    val scale = minOf(1.0, longEdge.toDouble() / maxOf(decoded.width, decoded.height))
    val dstW = maxOf(1, (decoded.width * scale).toInt())
    val dstH = maxOf(1, (decoded.height * scale).toInt())
    val scaled = if (dstW == decoded.width && dstH == decoded.height) {
        decoded
    } else {
        Bitmap.createScaledBitmap(decoded, dstW, dstH, true)
    }

    val pixels = IntArray(dstW * dstH)
    scaled.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
    if (scaled !== decoded) scaled.recycle()
    decoded.recycle()

    val gray = ByteArray(dstW * dstH)
    for (i in pixels.indices) {
        val c = pixels[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        // Rec. 601 luma (0.299 / 0.587 / 0.114), integer approximation: weights sum to 256.
        gray[i] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
    }
    return GrayImage(dstW, dstH, gray)
}

/** Largest power-of-two shrink that keeps the longest side at or above [target]. */
private fun sampleSizeFor(longestSide: Int, target: Int): Int {
    var sampleSize = 1
    while (longestSide / (sampleSize * 2) >= target) sampleSize *= 2
    return sampleSize
}
