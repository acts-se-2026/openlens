package com.openlens.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openlens.app.auth.AuthResult
import kotlinx.coroutines.launch

/**
 * Log in with an email OR username (both are Kratos password identifiers) plus a password. On
 * success [onLoggedIn] fires; the screen stays in its loading state because it's about to be
 * replaced. Errors surface inline without leaving the screen.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onNavigateToRegister: () -> Unit,
    login: suspend (identifier: String, password: String) -> AuthResult,
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canSubmit = identifier.isNotBlank() && password.isNotEmpty()

    fun submit() {
        if (loading || !canSubmit) return
        loading = true
        error = null
        scope.launch {
            when (val result = login(identifier, password)) {
                is AuthResult.Success -> onLoggedIn()
                is AuthResult.Error -> {
                    error = result.message
                    loading = false
                }
            }
        }
    }

    AuthScaffold(title = "Welcome back", subtitle = "Log in to keep scanning.") {
        AuthTextField(
            value = identifier,
            onValueChange = { identifier = it; error = null },
            label = "Email or username",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = !loading,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Password",
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            enabled = !loading,
        )
        AuthError(error)
        Spacer(Modifier.height(20.dp))
        AuthPrimaryButton(text = "Log in", enabled = canSubmit, loading = loading, onClick = { submit() })
        Spacer(Modifier.height(14.dp))
        AuthLinkRow(
            prompt = "New here?",
            action = "Create account",
            enabled = !loading,
            onClick = onNavigateToRegister,
        )
    }
}
