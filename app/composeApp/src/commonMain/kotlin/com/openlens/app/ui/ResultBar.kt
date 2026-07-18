package com.openlens.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.scan.ScanResult
import com.openlens.app.ui.theme.OpenLensColors

/**
 * Floating pill (caller sizes it to ~3/4 width). Two calm zones: the answer line — tap anywhere on it
 * to open the reading card (a quiet chevron hints at it) — and the single, clearly-labeled
 * "Identify this frame" button (spinner while [analyzing]). Retake lives elsewhere ([RetakeButton]).
 */
@Composable
fun ResultPill(
    result: ScanResult?,
    analyzing: Boolean,
    onIdentify: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = OpenLensColors.Surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = result != null, onClick = onOpenDetail)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result?.label ?: if (analyzing) "Identifying…" else "Frame the subject",
                    color = OpenLensColors.TextHi,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (result != null) {
                    ChevronDown(OpenLensColors.TextLo, modifier = Modifier.padding(start = 10.dp))
                }
            }

            // The 1-tap action: re-analyze the current frame.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(OpenLensColors.Accent.copy(alpha = if (analyzing) 0.6f else 1f))
                    .clickable(enabled = !analyzing, onClick = onIdentify)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (analyzing) {
                    CircularProgressIndicator(
                        color = OpenLensColors.OnAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ReticleIcon(OpenLensColors.OnAccent)
                        Text(
                            text = "Identify this frame",
                            color = OpenLensColors.OnAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Labeled "Retake" control for a top corner: a camera glyph + text on a dark scrim, so it's clearly
 * a "new photo" action rather than a bare, guessable icon. */
@Composable
fun RetakeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraIcon()
        Text(
            text = "Retake",
            color = OpenLensColors.TextHi,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

/**
 * Full reading card: heading + ✕, the description, and the similar-images masonry. The whole card is a
 * single vertical scroll — heading, text, and images move together — capped at a fraction of the
 * screen so tall content scrolls inside the sheet instead of sliding off the top. The scrim +
 * open/close animation are owned by the caller.
 */
@Composable
fun ResultDetailCard(
    result: ScanResult,
    similarState: SimilarImagesState,
    loadImage: suspend (String) -> ByteArray?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = OpenLensColors.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        BoxWithConstraints {
            val maxCardHeight = maxHeight * 0.85f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxCardHeight)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 24.dp, top = 16.dp, end = 14.dp, bottom = 22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.label,
                        color = OpenLensColors.TextHi,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onClose)
                            .padding(10.dp),
                    ) {
                        Text("✕", color = OpenLensColors.TextLo, fontSize = 18.sp)
                    }
                }
                Text(
                    text = result.detail,
                    color = OpenLensColors.TextLo,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 12.dp, end = 10.dp),
                )
                SimilarImagesSection(
                    state = similarState,
                    loadImage = loadImage,
                    modifier = Modifier.padding(top = 20.dp, end = 10.dp),
                )
            }
        }
    }
}

/** A small camera glyph. */
@Composable
private fun CameraIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = OpenLensColors.TextHi,
            topLeft = Offset(w * 0.30f, h * 0.18f),
            size = Size(w * 0.24f, h * 0.16f),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = stroke,
        )
        drawRoundRect(
            color = OpenLensColors.TextHi,
            topLeft = Offset(w * 0.06f, h * 0.30f),
            size = Size(w * 0.88f, h * 0.52f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = stroke,
        )
        drawCircle(
            color = OpenLensColors.TextHi,
            radius = w * 0.15f,
            center = Offset(w * 0.5f, h * 0.58f),
            style = stroke,
        )
    }
}

/** A small viewfinder reticle (four corner brackets) — reinforces "scan/identify the framed area". */
@Composable
private fun ReticleIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val arm = w * 0.34f
        val s = 1.8.dp.toPx()
        val cap = StrokeCap.Round
        drawLine(color, Offset(0f, arm), Offset(0f, 0f), s, cap)
        drawLine(color, Offset(0f, 0f), Offset(arm, 0f), s, cap)
        drawLine(color, Offset(w - arm, 0f), Offset(w, 0f), s, cap)
        drawLine(color, Offset(w, 0f), Offset(w, arm), s, cap)
        drawLine(color, Offset(0f, h - arm), Offset(0f, h), s, cap)
        drawLine(color, Offset(0f, h), Offset(arm, h), s, cap)
        drawLine(color, Offset(w - arm, h), Offset(w, h), s, cap)
        drawLine(color, Offset(w, h - arm), Offset(w, h), s, cap)
        drawCircle(color, radius = 1.2.dp.toPx(), center = Offset(w / 2f, h / 2f))
    }
}

/** A small downward chevron — the quiet "tap for details" cue on the answer line. */
@Composable
private fun ChevronDown(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 14.dp, height = 9.dp)) {
        val s = 1.8.dp.toPx()
        val midX = size.width / 2f
        drawLine(color, Offset(size.width * 0.12f, size.height * 0.28f), Offset(midX, size.height * 0.78f), s, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.88f, size.height * 0.28f), Offset(midX, size.height * 0.78f), s, StrokeCap.Round)
    }
}
