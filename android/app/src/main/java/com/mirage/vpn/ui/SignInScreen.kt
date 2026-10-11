package com.mirage.vpn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Shown only when the build embeds a default server and no profile is saved yet. */
@Composable
fun SignInScreen(
    onSignIn: (username: String, password: String) -> String?,
    onUseDifferentServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var userError by remember { mutableStateOf<String?>(null) }
    var passError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        userError = if (username.isBlank()) "Enter your username" else null
        passError = if (password.isEmpty()) "Enter your password" else null
        error = null
        if (userError == null && passError == null) error = onSignIn(username, password)
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlobeGlyph(MaterialTheme.colorScheme.primary, size = 48.dp)
        Text("Sign in", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Enter the account details you were given.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; userError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Username") },
            singleLine = true,
            isError = userError != null,
            supportingText = userError?.let { msg -> { Text(msg) } },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; passError = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            isError = passError != null,
            supportingText = passError?.let { msg -> { Text(msg) } },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = if (reveal) KeyboardType.Text else KeyboardType.Password),
            trailingIcon = {
                TextButton(
                    onClick = { reveal = !reveal },
                    modifier = Modifier.semantics { contentDescription = if (reveal) "Hide password" else "Show password" },
                ) { Text(if (reveal) "Hide" else "Show") }
            },
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
        TextButton(onClick = onUseDifferentServer) { Text("Use a different server") }
    }
}
