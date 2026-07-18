package com.openlens.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.openlens.app.scan.NormRect
import com.openlens.app.ui.theme.OpenLensColors

/**
 * A movable, resizable rectangle over a full-bleed photo, plus optional tappable [detections] (faint
 * outlines of objects a detector found). Controlled: the caller owns [rect] (normalized 0..1 of THIS
 * overlay's size) and gets changes via [onRectChange]. Drag the body to move; drag a corner/edge
 * handle to resize; tap a detection to snap the frame onto it ([onDetectionTap]). [detections] are in
 * the same overlay-normalized space as [rect].
 */
@Composable
fun EditableFrameOverlay(
    rect: NormRect,
    onRectChange: (NormRect) -> Unit,
    modifier: Modifier = Modifier,
    detections: List<NormRect> = emptyList(),
    onDetectionTap: (NormRect) -> Unit = {},
) {
    val latestRect by rememberUpdatedState(rect)
    val latestDetections by rememberUpdatedState(detections)
    var handle by remember { mutableStateOf<Handle?>(null) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w <= 0f || h <= 0f) return@detectTapGestures
                    val nx = pos.x / w
                    val ny = pos.y / h
                    val hit = latestDetections
                        .filter { nx >= it.left && nx <= it.right && ny >= it.top && ny <= it.bottom }
                        .minByOrNull { it.area }
                    if (hit != null) onDetectionTap(hit)
                }
            }
            .pointerInput(Unit) {
                val slopPx = 22.dp.toPx()
                val minSidePx = 44.dp.toPx()
                detectDragGestures(
                    onDragStart = { start ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        handle = when {
                            w <= 0f || h <= 0f -> null
                            else -> hitHandle(latestRect, start, w, h, slopPx)
                                ?: if (pxRect(latestRect, w, h).contains(start)) Handle.Body else null
                        }
                    },
                    onDragEnd = { handle = null },
                    onDragCancel = { handle = null },
                ) { change, dragAmount ->
                    val grabbed = handle ?: return@detectDragGestures
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w <= 0f || h <= 0f) return@detectDragGestures
                    change.consume()
                    onRectChange(
                        latestRect.drag(grabbed, dragAmount.x / w, dragAmount.y / h, minSidePx / w, minSidePx / h),
                    )
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val r = pxRect(rect, w, h)

        // Dim everything outside the framed region with four bands around it.
        val scrim = Color.Black.copy(alpha = 0.5f)
        drawRect(scrim, Offset(0f, 0f), Size(w, r.top))
        drawRect(scrim, Offset(0f, r.bottom), Size(w, h - r.bottom))
        drawRect(scrim, Offset(0f, r.top), Size(r.left, r.height))
        drawRect(scrim, Offset(r.right, r.top), Size(w - r.right, r.height))

        // Detected objects: faint tappable outlines (drawn over the scrim so they stay visible).
        detections.forEach { d ->
            drawRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(d.left * w, d.top * h),
                size = Size((d.right - d.left) * w, (d.bottom - d.top) * h),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        // The active frame outline.
        drawRect(
            color = OpenLensColors.Accent,
            topLeft = Offset(r.left, r.top),
            size = Size(r.width, r.height),
            style = Stroke(width = 2.dp.toPx()),
        )

        // Grab handles: white dot with an accent ring, at each corner and edge midpoint.
        val cx = (r.left + r.right) / 2f
        val cy = (r.top + r.bottom) / 2f
        val handleR = 6.dp.toPx()
        listOf(
            Offset(r.left, r.top), Offset(r.right, r.top),
            Offset(r.left, r.bottom), Offset(r.right, r.bottom),
            Offset(cx, r.top), Offset(cx, r.bottom),
            Offset(r.left, cy), Offset(r.right, cy),
        ).forEach { c ->
            drawCircle(Color.White, radius = handleR, center = c)
            drawCircle(OpenLensColors.Accent, radius = handleR, center = c, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Top, Bottom, Left, Right, Body }

private fun pxRect(n: NormRect, w: Float, h: Float): Rect =
    Rect(n.left * w, n.top * h, n.right * w, n.bottom * h)

private fun hitHandle(n: NormRect, point: Offset, w: Float, h: Float, slopPx: Float): Handle? {
    val cx = (n.left + n.right) / 2f
    val cy = (n.top + n.bottom) / 2f
    val candidates = listOf(
        Handle.TopLeft to Offset(n.left * w, n.top * h),
        Handle.TopRight to Offset(n.right * w, n.top * h),
        Handle.BottomLeft to Offset(n.left * w, n.bottom * h),
        Handle.BottomRight to Offset(n.right * w, n.bottom * h),
        Handle.Top to Offset(cx * w, n.top * h),
        Handle.Bottom to Offset(cx * w, n.bottom * h),
        Handle.Left to Offset(n.left * w, cy * h),
        Handle.Right to Offset(n.right * w, cy * h),
    )
    return candidates
        .filter { (_, c) -> (c - point).getDistance() <= slopPx }
        .minByOrNull { (_, c) -> (c - point).getDistance() }
        ?.first
}

private fun NormRect.drag(handle: Handle, dx: Float, dy: Float, minW: Float, minH: Float): NormRect {
    if (handle == Handle.Body) {
        val bw = right - left
        val bh = bottom - top
        val l = (left + dx).coerceIn(0f, 1f - bw)
        val t = (top + dy).coerceIn(0f, 1f - bh)
        return NormRect(l, t, l + bw, t + bh)
    }
    var l = left
    var t = top
    var r = right
    var b = bottom
    if (handle == Handle.Left || handle == Handle.TopLeft || handle == Handle.BottomLeft) l = (l + dx).coerceIn(0f, r - minW)
    if (handle == Handle.Right || handle == Handle.TopRight || handle == Handle.BottomRight) r = (r + dx).coerceIn(l + minW, 1f)
    if (handle == Handle.Top || handle == Handle.TopLeft || handle == Handle.TopRight) t = (t + dy).coerceIn(0f, b - minH)
    if (handle == Handle.Bottom || handle == Handle.BottomLeft || handle == Handle.BottomRight) b = (b + dy).coerceIn(t + minH, 1f)
    return NormRect(l, t, r, b)
}
