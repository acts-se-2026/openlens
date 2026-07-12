package com.openlens.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.openlens.app.ui.CaptureScreen
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.ui.theme.OpenLensTheme

@Composable
fun App() {
    OpenLensTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = OpenLensColors.Bg) {
            CaptureScreen(
                onCapture = {
                    // TODO: trigger scan flow (Scanning → Result) against the fake repo
                }
            )
        }
    }
}
