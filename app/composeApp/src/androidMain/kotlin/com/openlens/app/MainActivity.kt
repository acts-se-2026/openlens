package com.openlens.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openlens.app.auth.OidcBridge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind fully transparent system bars (the camera fills the screen; the composables
        // already inset themselves). SystemBarStyle.dark = light icons over our dark UI.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
        // If the app was launched cold by the OIDC redirect, this carries the code.
        handleOidcRedirect(intent)
    }

    // singleTask (see manifest): when the OIDC redirect arrives while we're already running, it
    // comes here rather than starting a new activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOidcRedirect(intent)
    }

    /** Pull `?code=…` off an `openlens://oidc-callback` VIEW intent and forward it to the bridge. */
    private fun handleOidcRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "openlens" && data.host == "oidc-callback") {
            data.getQueryParameter("code")?.let { OidcBridge.deliver(it) }
        }
    }
}
