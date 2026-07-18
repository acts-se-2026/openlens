package com.openlens.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.ui.theme.OpenLensColors

/** Muted red for auth errors — needs to read as "problem", not blend into hint text. */
private val AuthErrorColor = Color(0xFFFF6B6B)

/**
 * Shared shell for the auth screens: brand wordmark over a centered dark card on the near-black
 * background, scrollable and inset-aware so the keyboard never covers the fields. [content] runs
 * inside the card's column.
 */
@Composable
internal fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OpenLensColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "OpenLens",
                color = OpenLensColors.TextHi,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(OpenLensColors.Surface)
                    .padding(24.dp),
            ) {
                Text(
                    text = title,
                    color = OpenLensColors.TextHi,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = subtitle, color = OpenLensColors.TextLo, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

/** The app's first text field. Styled to [OpenLensColors]; password fields mask input. */
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onGo = { onImeAction() }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OpenLensColors.Accent,
            unfocusedBorderColor = OpenLensColors.TextLo.copy(alpha = 0.4f),
            disabledBorderColor = OpenLensColors.TextLo.copy(alpha = 0.2f),
            focusedLabelColor = OpenLensColors.Accent,
            unfocusedLabelColor = OpenLensColors.TextLo,
            focusedTextColor = OpenLensColors.TextHi,
            unfocusedTextColor = OpenLensColors.TextHi,
            disabledTextColor = OpenLensColors.TextLo,
            cursorColor = OpenLensColors.Accent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Primary CTA — the near-white filled button shape used across OpenLens (see BlurWarning). */
@Composable
internal fun AuthPrimaryButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OpenLensColors.Accent.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = OpenLensColors.OnAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = text,
                color = OpenLensColors.OnAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Outlined "Continue with Google" button — the multicolour Google mark left of the label. */
@Composable
internal fun GoogleSignInButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = OpenLensColors.TextLo.copy(alpha = if (enabled) 0.5f else 0.2f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            imageVector = GoogleLogo,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Continue with Google",
            color = if (enabled) OpenLensColors.TextHi else OpenLensColors.TextLo,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** "New here? Create account" style link row switching between the two auth screens. */
@Composable
internal fun AuthLinkRow(prompt: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text = prompt, color = OpenLensColors.TextLo, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = action,
            color = OpenLensColors.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        )
    }
}

/** Inline error line under the fields; renders nothing when [message] is null. */
@Composable
internal fun AuthError(message: String?) {
    if (message != null) {
        Spacer(Modifier.height(12.dp))
        Text(text = message, color = AuthErrorColor, fontSize = 13.sp)
    }
}
