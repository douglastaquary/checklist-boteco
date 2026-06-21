package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.checklistboteco.domain.model.User
import com.checklistboteco.platform.AppNetworkFeedback
import com.checklistboteco.presentation.viewmodel.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (User) -> Unit,
    onNewUserClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isNetworkLoading by AppNetworkFeedback.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklist Boteco") }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Bem-vindo!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = state.userName,
                onValueChange = viewModel::updateUserName,
                label = { Text("Usuário ou email") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.pendingBiometricUnlock && !state.biometricUnlockInProgress
            )
            Spacer(Modifier.height(16.dp))

            if (state.pendingBiometricUnlock) {
                Text(
                    "Login salvo neste aparelho. Confirme sua biometria para preencher usuário e senha.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::unlockRememberedUser,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.biometricUnlockInProgress
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Text(
                            if (state.biometricUnlockInProgress) "Aguardando biometria..." else "Usar biometria",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::updatePassword,
                label = { Text("Senha") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !state.pendingBiometricUnlock && !state.biometricUnlockInProgress
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.rememberCredentials,
                    onCheckedChange = viewModel::updateRememberCredentials
                )
                Text(
                    "Lembrar login (biometria)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (state.requiresTwoFactor) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.twoFactorCode,
                    onValueChange = viewModel::updateTwoFactorCode,
                    label = { Text("Código de verificação") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            state.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = if (state.requiresTwoFactor) viewModel::verifyTwoFactor else viewModel::login,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isNetworkLoading && !state.biometricUnlockInProgress && !state.pendingBiometricUnlock
            ) {
                Text(
                    when {
                        isNetworkLoading -> "Aguarde..."
                        state.requiresTwoFactor -> "Confirmar dispositivo"
                        else -> "Entrar"
                    }
                )
            }

            TextButton(onClick = onNewUserClick) {
                Text("Novo usuário")
            }
        }
    }

    LaunchedEffect(state.isLoggedIn, state.currentUser) {
        if (state.isLoggedIn && state.currentUser != null) {
            onLoginSuccess(state.currentUser!!)
        }
    }
}
