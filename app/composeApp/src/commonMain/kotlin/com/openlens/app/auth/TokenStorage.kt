package com.openlens.app.auth

import androidx.compose.runtime.Composable
import com.liftric.kvault.KVault

/**
 * Secure on-device storage for the Kratos session token (the `ory_st_…` string), backed by KVault
 * (Android EncryptedSharedPreferences + Keystore, iOS Keychain) — never plain prefs. Kratos uses a
 * single opaque session token (no access/refresh split, no rotation), so there's just one value to
 * hold. KVault's ops are synchronous, so no suspend is needed.
 */
class TokenStorage(private val kvault: KVault) {

    fun save(sessionToken: String) {
        kvault.set(KEY_SESSION, sessionToken)
    }

    fun sessionToken(): String? = kvault.string(forKey = KEY_SESSION)

    /** A restorable session = we still hold a session token (validity is confirmed with Kratos). */
    fun hasSession(): Boolean = sessionToken() != null

    fun clear() {
        kvault.deleteObject(forKey = KEY_SESSION)
    }

    private companion object {
        const val KEY_SESSION = "auth.session_token"
    }
}

/**
 * Platform-backed [TokenStorage], remembered for the composition. Android needs a `Context` (read
 * from `LocalContext`); iOS talks straight to the Keychain. Mirrors `rememberImagePickerLauncher`,
 * the app's existing "composable that needs platform context" pattern.
 */
@Composable
expect fun rememberTokenStorage(): TokenStorage
