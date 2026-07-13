package com.openlens.app.util

import androidx.compose.ui.graphics.ImageBitmap

/** Decode captured JPEG/encoded bytes into a Compose ImageBitmap (platform-specific decoder). */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap
