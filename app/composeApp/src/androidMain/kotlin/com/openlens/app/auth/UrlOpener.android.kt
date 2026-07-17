package com.openlens.app.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberUrlOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            // ACTION_VIEW → the user's default browser (a full browser, not a WebView, so Google's
            // session and password autofill work). After auth it redirects to openlens://… which
            // Android routes back to MainActivity's deep-link intent-filter.
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
