package com.openlens.app.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.openlens.app.util.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual class ImagePickerLauncher internal constructor(
    private val onResult: (ByteArray?) -> Unit,
) {
    // PHPickerViewController only holds its delegate weakly; keep it alive until the flow ends.
    private var delegate: PickerDelegate? = null

    actual fun launch() {
        val presenter = topViewController()
        if (presenter == null) {
            onResult(null)
            return
        }
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1L
        }
        val pickerDelegate = PickerDelegate { bytes, picked ->
            delegate = null
            if (picked) onResult(bytes) // dismissed without choosing stays silent
        }
        delegate = pickerDelegate
        val picker = PHPickerViewController(configuration = configuration)
        picker.delegate = pickerDelegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

@Composable
actual fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): ImagePickerLauncher {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember { ImagePickerLauncher { bytes -> currentOnResult(bytes) } }
}

/** Loads the picked item's data, converts it to an upright JPEG, and reports on the main queue. */
private class PickerDelegate(
    private val onFinished: (bytes: ByteArray?, picked: Boolean) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onFinished(null, false)
            return
        }
        val provider = result.itemProvider
        if (!provider.hasItemConformingToTypeIdentifier("public.image")) {
            onFinished(null, true)
            return
        }
        provider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            // The completion arrives on a background queue; decoding and re-encoding are safe
            // there, but report on the main queue since the callback mutates Compose state.
            val jpeg = data?.let { UIImage.imageWithData(it) }?.let(::toUprightJpeg)
            dispatch_async(dispatch_get_main_queue()) { onFinished(jpeg, true) }
        }
    }
}

private const val MAX_DIMENSION = 2560.0
private const val JPEG_QUALITY = 0.9

/**
 * Redraw the image so EXIF orientation is baked into the pixels (the Skia decoder used for the
 * preview ignores the tag) and the longest side is capped, then encode as JPEG — library photos
 * are often HEIC, which the backend wouldn't accept.
 */
@OptIn(ExperimentalForeignApi::class)
private fun toUprightJpeg(image: UIImage): ByteArray? {
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val pixelWidth = width * image.scale
    val pixelHeight = height * image.scale
    val shrink = minOf(1.0, MAX_DIMENSION / maxOf(pixelWidth, pixelHeight))
    val targetWidth = pixelWidth * shrink
    val targetHeight = pixelHeight * shrink

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), false, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    val redrawn = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    val data = UIImageJPEGRepresentation(redrawn ?: return null, JPEG_QUALITY) ?: return null
    return data.toByteArray()
}

/** The view controller currently on top of the (Compose-hosted) hierarchy, to present from. */
private fun topViewController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.keyWindow ?: application.windows.firstOrNull() as? UIWindow
    var top = window?.rootViewController ?: return null
    while (true) {
        top = top.presentedViewController ?: return top
    }
}
