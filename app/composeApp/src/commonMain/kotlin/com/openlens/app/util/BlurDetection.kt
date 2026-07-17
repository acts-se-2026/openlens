package com.openlens.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A downscaled, tightly-packed 8-bit grayscale image: [pixels] is row-major, exactly
 * [width] * [height] bytes, one luminance sample (0..255, read as an unsigned byte) per pixel and no
 * row padding. Produced by [toGrayscaleDownscaled], consumed by [laplacianVariance].
 */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray)

/**
 * Client-side "is this shot too blurry?" gate. Uses the variance-of-the-Laplacian focus measure —
 * the classic OpenCV blur trick, hand-rolled so we ship no native CV library. A sharp photo has lots
 * of edge energy and scores high; an out-of-focus or motion-blurred one is smooth and scores low.
 * See planning/blur-detection.md.
 */
object BlurCheck {
    /**
     * Longest edge (px) the image is scaled down to before measuring. The downscale is what makes
     * this cheap (a ~500px pass is a few ms), but it also means [THRESHOLD] is only valid at THIS
     * size — variance scales with resolution and edge count. Change one, recalibrate the other.
     */
    const val LONG_EDGE = 500

    /**
     * Below this variance-of-Laplacian value a shot is treated as too blurry. Deliberately a plain,
     * tweakable constant — calibrate it against real photos (Model/ImageDataset has a blurry sample
     * plus sharp ones). It only ever drives a *soft* warning in the UI, so an off value costs the
     * user one extra tap, never a trapped capture.
     */
    const val THRESHOLD = 100.0

    /**
     * Measure how sharp an encoded image ([bytes] = JPEG/PNG/…) is. Returns the focus score, or null
     * when the bytes can't be decoded — the caller treats null as "can't tell" and lets the shot
     * through rather than blocking on a measurement it couldn't make. Decoding runs on
     * [Dispatchers.Default], so this is safe to call from the UI.
     */
    suspend fun score(bytes: ByteArray): Double? = withContext(Dispatchers.Default) {
        val gray = toGrayscaleDownscaled(bytes, LONG_EDGE) ?: return@withContext null
        laplacianVariance(gray)
    }

    /** True when [bytes] scores below [THRESHOLD]. Undecodable input is never called blurry. */
    suspend fun isBlurry(bytes: ByteArray): Boolean {
        val s = score(bytes) ?: return false
        return s < THRESHOLD
    }
}

/**
 * Variance of the 3×3 Laplacian response over [img] — the focus measure behind [BlurCheck]. Written
 * once in common code so Android and iOS produce the same number for the same pixels, which is what
 * lets a single [BlurCheck.THRESHOLD] govern both platforms.
 *
 * Kernel (4-neighbour):
 * ```
 *  0  1  0
 *  1 -4  1
 *  0  1  0
 * ```
 *
 * Rotation-invariant, so the source's EXIF orientation is irrelevant. Interior pixels only — the 1px
 * border has no full neighbourhood. Returns [Double.MAX_VALUE] for images too small to have an
 * interior, so a degenerate input is never flagged as blurry.
 */
fun laplacianVariance(img: GrayImage): Double {
    val w = img.width
    val h = img.height
    if (w < 3 || h < 3) return Double.MAX_VALUE
    val p = img.pixels

    var sum = 0.0
    var sumSq = 0.0
    var n = 0
    for (y in 1 until h - 1) {
        val row = y * w
        for (x in 1 until w - 1) {
            val i = row + x
            val center = p[i].toInt() and 0xFF
            val up = p[i - w].toInt() and 0xFF
            val down = p[i + w].toInt() and 0xFF
            val left = p[i - 1].toInt() and 0xFF
            val right = p[i + 1].toInt() and 0xFF
            val lap = (up + down + left + right - 4 * center).toDouble()
            sum += lap
            sumSq += lap * lap
            n++
        }
    }
    if (n == 0) return Double.MAX_VALUE
    val mean = sum / n
    return sumSq / n - mean * mean
}

/**
 * Decode [bytes], scale so the longest edge is [longEdge] px, and hand back tightly-packed 8-bit
 * grayscale ([GrayImage]). Returns null if the bytes can't be decoded. Implementations decode
 * downsampled where the platform allows, so a full-resolution bitmap is never materialised.
 */
internal expect fun toGrayscaleDownscaled(bytes: ByteArray, longEdge: Int): GrayImage?
