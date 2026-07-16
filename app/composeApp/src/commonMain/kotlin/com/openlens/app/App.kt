package com.openlens.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.auth.AuthGraph
import com.openlens.app.auth.rememberTokenStorage
import com.openlens.app.camera.CameraPreview
import com.openlens.app.camera.rememberCameraController
import com.openlens.app.scan.ScanResult
import com.openlens.app.ui.CaptureScreen
import com.openlens.app.ui.LoginScreen
import com.openlens.app.ui.RegisterScreen
import com.openlens.app.ui.ResultScreen
import com.openlens.app.ui.ScanningScreen
import com.openlens.app.ui.theme.OpenLensColors
import com.openlens.app.ui.theme.OpenLensTheme
import kotlinx.coroutines.launch

private sealed interface Screen {
    /** Startup: checking for a restorable session before showing auth or the camera. */
    data object Restoring : Screen
    data object Login : Screen
    data object Register : Screen
    data object Capture : Screen
    data class Scanning(val bytes: ByteArray) : Screen
    data class Result(val bytes: ByteArray, val result: ScanResult) : Screen
}

@Composable
fun App() {
    OpenLensTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = OpenLensColors.Bg) {
            // Secure token store wired once into the process-level graph. The clients (public for
            // auth, authed w/ refresh for gated calls) are singletons there, so they survive Activity
            // recreation instead of leaking an engine per rotation. Scans use the authed client so
            // /image_to_model carries the bearer.
            val tokenStorage = rememberTokenStorage()
            remember(tokenStorage) { AuthGraph.ensureInitialized(tokenStorage) }
            val authRepository = AuthGraph.authRepository
            val repository = AuthGraph.scanRepository
            val controller = rememberCameraController()
            val scope = rememberCoroutineScope()

            var screen: Screen by remember { mutableStateOf(Screen.Restoring) }

            // Restore an existing login before showing anything: a valid session skips straight to
            // the camera; otherwise (no token, or refresh rejected) land on Login.
            LaunchedEffect(Unit) {
                screen = if (authRepository.validateSession()) Screen.Capture else Screen.Login
            }

            Box(Modifier.fillMaxSize()) {
                // One long-lived camera preview under Capture + Scanning only — never under auth,
                // the splash, or Result. Kept across the capture→scan hop so the camera isn't
                // re-warmed between a shot and its scan; Scanning/Result paint an opaque frame over it.
                if (screen is Screen.Capture || screen is Screen.Scanning) {
                    CameraPreview(controller, Modifier.fillMaxSize())
                }

                when (val current = screen) {
                    Screen.Restoring -> RestoringScreen()

                    Screen.Login ->
                        LoginScreen(
                            onLoggedIn = { screen = Screen.Capture },
                            onNavigateToRegister = { screen = Screen.Register },
                            login = authRepository::login,
                        )

                    Screen.Register ->
                        RegisterScreen(
                            onRegistered = { screen = Screen.Capture },
                            onNavigateToLogin = { screen = Screen.Login },
                            register = authRepository::register,
                        )

                    Screen.Capture ->
                        CaptureScreen(
                            controller = controller,
                            onCaptured = { bytes -> screen = Screen.Scanning(bytes) },
                            onLogout = {
                                scope.launch {
                                    authRepository.logout()
                                    screen = Screen.Login
                                }
                            },
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

/** Brief startup splash while the session is being restored. */
@Composable
private fun RestoringScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "OpenLens",
                color = OpenLensColors.TextHi,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            CircularProgressIndicator(
                color = OpenLensColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.padding(top = 20.dp).size(22.dp),
            )
        }
    }
}
