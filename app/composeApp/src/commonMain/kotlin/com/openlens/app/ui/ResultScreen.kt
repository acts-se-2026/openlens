package com.openlens.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.scan.ScanResult
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.util.decodeImageBitmap

/** Single best answer over the captured frame. */
@Composable
fun ResultScreen(bytes: ByteArray, result: ScanResult, onScanAgain: () -> Unit) {
    val image = remember(bytes) { decodeImageBitmap(bytes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OpenLensColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(24.dp)) {
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
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Scan again", color = OpenLensColors.Accent, fontSize = 16.sp)
            }
        }
    }
}
