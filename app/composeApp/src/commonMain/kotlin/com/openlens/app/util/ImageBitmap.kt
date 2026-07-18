package com.openlens.app.util

import androidx.compose.ui.graphics.ImageBitmap

/** Decode captured JPEG/encoded bytes into a Compose ImageBitmap (platform-specific decoder). */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap

/**
 * Decode encoded bytes into an ImageBitmap whose longest edge is at most [maxDimensionPx], keeping
 * memory bounded regardless of the source's real dimensions (a small JPEG can still be a huge bitmap).
 * Used for the many concurrently-loaded similar-image thumbnails. Returns null on any decode failure
 * (including out-of-memory) so the caller can show a placeholder instead of crashing.
 */
expect fun decodeImageBitmapSampled(bytes: ByteArray, maxDimensionPx: Int): ImageBitmap?
