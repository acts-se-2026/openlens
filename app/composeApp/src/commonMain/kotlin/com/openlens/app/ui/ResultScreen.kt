package com.openlens.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.scan.ScanResult
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.util.decodeImageBitmap

/**
 * Single best answer over the captured frame. The photo stays full-bleed (same size it had while
 * scanning — no resize/jump), and the answer slides up as a bottom sheet over it.
 */
@Composable
fun ResultScreen(bytes: ByteArray, result: ScanResult, onScanAgain: () -> Unit) {
    val image = remember(bytes) { decodeImageBitmap(bytes) }

    Box(modifier = Modifier.fillMaxSize().background(OpenLensColors.Bg)) {
        // Full-bleed frozen frame — identical framing to the scanning state, so nothing jumps.
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Animate the sheet up once, on first composition.
        var sheetVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { sheetVisible = true }

        AnimatedVisibility(
            visible = sheetVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ResultSheet(result = result, onScanAgain = onScanAgain)
        }
    }
}

/** Rounded-top bottom sheet holding the answer, floating over the photo. */
@Composable
private fun ResultSheet(result: ScanResult, onScanAgain: () -> Unit) {
    Surface(
        color = OpenLensColors.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 16.dp),
        ) {
            // Grabber handle — reads as a draggable sheet.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(OpenLensColors.TextLo.copy(alpha = 0.4f)),
            )
            Text(
                text = result.label,
                color = OpenLensColors.TextHi,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = result.detail,
                color = OpenLensColors.TextLo,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(
                onClick = onScanAgain,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Scan again", color = OpenLensColors.Accent, fontSize = 16.sp)
            }
        }
    }
}
