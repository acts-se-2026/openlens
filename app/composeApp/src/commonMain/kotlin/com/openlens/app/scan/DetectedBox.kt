package com.openlens.app.scan

/**
 * One object the detector found in the captured frame. [rect] is the object's box in normalized
 * 0..1 image coordinates — the same frame the photo is displayed in — so the UI maps it onto the
 * on-screen photo with the image's display transform. [id] is a stable per-detection key the UI
 * uses to track the current selection and to cache the analysis result for each box.
 */
data class DetectedBox(
    val id: String,
    val label: String,
    val confidence: Float,
    val rect: NormRect,
)

/**
 * An axis-aligned rectangle in normalized 0..1 image coordinates ((0,0) = top-left of the image,
 * (1,1) = bottom-right). Independent of pixels or screen size, so it survives rotation and maps onto
 * whatever size the photo is drawn at.
 */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
}
