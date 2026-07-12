package com.openlens.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.openlens.app.scan.FakeScanRepository
import com.openlens.app.scan.ScanResult
import com.openlens.app.ui.CaptureScreen
import com.openlens.app.ui.ResultScreen
import com.openlens.app.ui.ScanningScreen
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.ui.theme.OpenLensTheme

private sealed interface Screen {
    data object Capture : Screen
    data class Scanning(val bytes: ByteArray) : Screen
    data class Result(val bytes: ByteArray, val result: ScanResult) : Screen
}

@Composable
fun App() {
    OpenLensTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = OpenLensColors.Bg) {
            val repository = remember { FakeScanRepository() }
            var screen: Screen by remember { mutableStateOf(Screen.Capture) }

            when (val current = screen) {
                is Screen.Capture ->
                    CaptureScreen(onCaptured = { bytes -> screen = Screen.Scanning(bytes) })

                is Screen.Scanning -> {
                    ScanningScreen()
                    LaunchedEffect(current) {
                        val result = repository.identify(current.bytes)
                        screen = Screen.Result(current.bytes, result)
                    }
                }

                is Screen.Result ->
                    ResultScreen(
                        bytes = current.bytes,
                        result = current.result,
                        onScanAgain = { screen = Screen.Capture },
                    )
            }
        }
    }
}
