package com.openlens.app.auth

/**
 * Tiny hand-off point for the OIDC deep-link return. The browser redirects to
 * `openlens://oidc-callback?code=…`; the platform entry point (Android's MainActivity) parses that
 * `code` and calls [deliver], which routes it to the listener the [com.openlens.app.App] composable
 * registered via [onCode]. This decouples "an Intent arrived" from "Compose reacts to it".
 *
 * Process-level singleton (the round-trip leaves and re-enters the app), single-listener by design —
 * only the active auth UI cares.
 */
object OidcBridge {
    private var pending: String? = null

    var onCode: ((String) -> Unit)? = null
        set(value) {
            field = value
            // If a code arrived before the listener registered (e.g. deep link raced composition),
            // flush it now.
            if (value != null) pending?.let { code -> pending = null; value(code) }
        }

    fun deliver(code: String) {
        val listener = onCode
        if (listener != null) listener(code) else pending = code
    }
}
