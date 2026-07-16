package com.openlens.app.auth

import androidx.compose.runtime.Composable
import com.liftric.kvault.KVault

/**
 * Secure on-device storage for the JWT access + refresh tokens, backed by KVault (Android
 * EncryptedSharedPreferences + Keystore, iOS Keychain). Both tokens — the long-lived refresh token
 * especially — live in platform secure storage, never plain prefs. KVault's ops are synchronous, so
 * no suspend is needed.
 */
class TokenStorage(private val kvault: KVault) {

    fun save(accessToken: String, refreshToken: String) {
        kvault.set(KEY_ACCESS, accessToken)
        kvault.set(KEY_REFRESH, refreshToken)
    }

    fun accessToken(): String? = kvault.string(forKey = KEY_ACCESS)

    fun refreshToken(): String? = kvault.string(forKey = KEY_REFRESH)

    /** A restorable session = we still hold a refresh token (access alone is short-lived). */
    fun hasSession(): Boolean = refreshToken() != null

    fun clear() {
        kvault.deleteObject(forKey = KEY_ACCESS)
        kvault.deleteObject(forKey = KEY_REFRESH)
    }

    private companion object {
        const val KEY_ACCESS = "auth.access_token"
        const val KEY_REFRESH = "auth.refresh_token"
    }
}

/**
 * Platform-backed [TokenStorage], remembered for the composition. Android needs a `Context` (read
 * from `LocalContext`); iOS talks straight to the Keychain. Mirrors `rememberImagePickerLauncher`,
 * the app's existing "composable that needs platform context" pattern.
 */
@Composable
expect fun rememberTokenStorage(): TokenStorage
