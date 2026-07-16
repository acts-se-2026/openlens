package com.openlens.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.openlens.app.camera.CameraPreview
import com.openlens.app.camera.rememberCameraController
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
            val controller = rememberCameraController()
            var screen: Screen by remember { mutableStateOf(Screen.Capture) }

            Box(Modifier.fillMaxSize()) {
                // One long-lived camera preview under everything, kept in composition across Capture
                // and Scanning and only released when the description (Result) screen takes over — so
                // the camera isn't re-warmed between a shot and its scan, and the auto-retake / retake
                // flow always shoots through an already-warm camera. Scanning and Result paint an
                // opaque frozen frame over it, so it's only ever visible on the Capture screen.
                if (screen !is Screen.Result) {
                    CameraPreview(controller, Modifier.fillMaxSize())
                }

                when (val current = screen) {
                    is Screen.Capture ->
                        CaptureScreen(
                            controller = controller,
                            onCaptured = { bytes -> screen = Screen.Scanning(bytes) },
                        )

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
}
