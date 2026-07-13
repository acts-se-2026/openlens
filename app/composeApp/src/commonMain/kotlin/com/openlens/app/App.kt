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
import com.openlens.app.scan.RemoteScanRepository
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
            val repository = remember { RemoteScanRepository() }
            var screen: Screen by remember { mutableStateOf(Screen.Capture) }

            when (val current = screen) {
                is Screen.Capture ->
                    CaptureScreen(onCaptured = { bytes -> screen = Screen.Scanning(bytes) })

                is Screen.Scanning -> {
                    ScanningScreen(bytes = current.bytes)
                    LaunchedEffect(current) {
                        val result = try {
                            repository.identify(current.bytes)
                        } catch (e: Exception) {
                            // Show the failure on the result sheet instead of crashing the app.
                            ScanResult(
                                label = "Couldn't reach the server",
                                detail = e.message ?: "Unknown error",
                            )
                        }
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
