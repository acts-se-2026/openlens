package com.openlens.app.auth

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

// NOTE: iOS OIDC isn't wired up/tested yet (Phase 2 targets Android). This satisfies the expect
// declaration and opens the URL in Safari; the deep-link return handling on iOS is still TODO.
@Composable
actual fun rememberUrlOpener(): (String) -> Unit = { url ->
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) }
}
