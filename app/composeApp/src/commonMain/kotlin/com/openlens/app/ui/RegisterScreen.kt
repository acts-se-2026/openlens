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
 * Create an account (username + email + password). The backend auto-logs-in on register (returns a
 * token pair), so on success we go straight into the app via [onRegistered]. Light client-side
 * checks keep obviously-bad submits from round-tripping; the backend is the real validator.
 */
@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onNavigateToLogin: () -> Unit,
    register: suspend (username: String, email: String, password: String) -> AuthResult,
    onGoogleSignIn: () -> Unit,
    googleError: String?,
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canSubmit = username.trim().length >= 3 && email.isNotBlank() && password.length >= 8

    fun submit() {
        if (loading || !canSubmit) return
        loading = true
        error = null
        scope.launch {
            when (val result = register(username, email, password)) {
                is AuthResult.Success -> onRegistered()
                is AuthResult.Error -> {
                    error = result.message
                    loading = false
                }
            }
        }
    }

    AuthScaffold(title = "Create account", subtitle = "One account, all your scans.") {
        AuthTextField(
            value = username,
            onValueChange = { username = it; error = null },
            label = "Username",
            imeAction = ImeAction.Next,
            enabled = !loading,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = "Email",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = !loading,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Password (8+ characters)",
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            enabled = !loading,
        )
        AuthError(error ?: googleError)
        Spacer(Modifier.height(20.dp))
        AuthPrimaryButton(
            text = "Create account",
            enabled = canSubmit,
            loading = loading,
            onClick = { submit() },
        )
        Spacer(Modifier.height(12.dp))
        GoogleSignInButton(enabled = !loading, onClick = onGoogleSignIn)
        Spacer(Modifier.height(14.dp))
        AuthLinkRow(
            prompt = "Already have an account?",
            action = "Log in",
            enabled = !loading,
            onClick = onNavigateToLogin,
        )
    }
}
