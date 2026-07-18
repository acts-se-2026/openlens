package com.openlens.app.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(bytes).toComposeImageBitmap()

actual fun decodeImageBitmapSampled(bytes: ByteArray, maxDimensionPx: Int): ImageBitmap? = try {
    val image = Image.makeFromEncoded(bytes)
    val longEdge = maxOf(image.width, image.height)
    if (longEdge <= 0 || longEdge <= maxDimensionPx) {
        image.toComposeImageBitmap()
    } else {
        // Render the image into a smaller raster surface so we hold a bounded bitmap, not the full-res one.
        val scale = maxDimensionPx.toFloat() / longEdge
        val targetWidth = (image.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (image.height * scale).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(targetWidth, targetHeight)
        surface.canvas.drawImageRect(image, Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat()))
        surface.makeImageSnapshot().toComposeImageBitmap()
    }
} catch (_: Throwable) {
    null
}
