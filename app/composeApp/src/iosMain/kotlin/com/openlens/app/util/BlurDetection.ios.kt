package com.openlens.app.util

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetBytesPerRow
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImage
import platform.posix.memcpy

/**
 * Decode with UIImage, then draw into a device-gray [CGBitmapContext] at the target size — Core
 * Graphics does the downscale and the luminance conversion in one step, handing back an 8-bit
 * grayscale buffer directly. Blur is rotation-invariant, so orientation is deliberately ignored.
 *
 * (The bytes reaching here are already size-capped upstream. If full-res camera frames ever hit this
 * path, ImageIO's CGImageSourceCreateThumbnailAtIndex would decode downsampled and avoid the full
 * UIImage allocation — a later optimisation, not needed today.)
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun toGrayscaleDownscaled(bytes: ByteArray, longEdge: Int): GrayImage? {
    val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
    val cgImage = image.CGImage ?: return null

    val srcW = CGImageGetWidth(cgImage).toInt()
    val srcH = CGImageGetHeight(cgImage).toInt()
    if (srcW <= 0 || srcH <= 0) return null

    val scale = minOf(1.0, longEdge.toDouble() / maxOf(srcW, srcH))
    val dstW = maxOf(1, (srcW * scale).toInt())
    val dstH = maxOf(1, (srcH * scale).toInt())

    val colorSpace = CGColorSpaceCreateDeviceGray()
    val context = CGBitmapContextCreate(
        data = null,
        width = dstW.convert(),
        height = dstH.convert(),
        bitsPerComponent = 8.convert(),
        bytesPerRow = dstW.convert(), // request tight rows; Core Graphics may still pad (checked below)
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNone.value,
    )
    CGColorSpaceRelease(colorSpace)
    if (context == null) return null

    CGContextDrawImage(context, CGRectMake(0.0, 0.0, dstW.toDouble(), dstH.toDouble()), cgImage)

    val base = CGBitmapContextGetData(context)
    if (base == null) {
        CGContextRelease(context)
        return null
    }
    val bytesPerRow = CGBitmapContextGetBytesPerRow(context).toInt()
    val src = base.reinterpret<ByteVar>()

    val gray = ByteArray(dstW * dstH)
    gray.usePinned { pinned ->
        if (bytesPerRow == dstW) {
            memcpy(pinned.addressOf(0), src, (dstW.toLong() * dstH).convert())
        } else {
            // Padded rows: copy the meaningful dstW bytes of each row into the tight buffer.
            for (y in 0 until dstH) {
                memcpy(pinned.addressOf(y * dstW), src + (y.toLong() * bytesPerRow), dstW.convert())
            }
        }
    }
    CGContextRelease(context)
    return GrayImage(dstW, dstH, gray)
}
