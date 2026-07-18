package com.openlens.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.liftric.kvault.KVault

@Composable
actual fun rememberTokenStorage(): TokenStorage {
    // KVault on Android wraps EncryptedSharedPreferences, which needs a Context.
    val context = LocalContext.current.applicationContext
    return remember { TokenStorage(KVault(context)) }
}
