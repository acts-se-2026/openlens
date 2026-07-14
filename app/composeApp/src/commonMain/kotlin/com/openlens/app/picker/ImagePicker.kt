package com.openlens.app.picker

import androidx.compose.runtime.Composable

/**
 * Opens the platform photo picker (Android Photo Picker / iOS PHPicker) so the user can scan an
 * existing image instead of capturing one. Neither picker needs a runtime permission.
 */
expect class ImagePickerLauncher {
    fun launch()
}

/**
 * [onResult] receives the picked image re-encoded as an upright JPEG — the only format the backend
 * accepts and the one the preview decoders expect — or null when the chosen image couldn't be
 * read. Dismissing the picker without choosing anything calls nothing.
 */
@Composable
expect fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): ImagePickerLauncher
