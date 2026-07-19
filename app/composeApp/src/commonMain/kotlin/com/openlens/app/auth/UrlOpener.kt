package com.openlens.app.auth

import androidx.compose.runtime.Composable

/**
 * Opens a URL in the platform's browser — used to hand the Google consent page off to a real
 * browser for OIDC (never a WebView; the browser holds the user's Google session and returns
 * control via the `openlens://` deep link). Returns a launcher lambda, mirroring the app's other
 * platform-context composables (`rememberTokenStorage`).
 */
@Composable
expect fun rememberUrlOpener(): (String) -> Unit
