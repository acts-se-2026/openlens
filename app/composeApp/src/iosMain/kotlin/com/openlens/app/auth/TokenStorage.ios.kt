package com.openlens.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.liftric.kvault.KVault

@Composable
actual fun rememberTokenStorage(): TokenStorage {
    // KVault on iOS is backed by the Keychain; the default service name (bundle id) is fine.
    return remember { TokenStorage(KVault()) }
}
