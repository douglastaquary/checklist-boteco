package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.presentation.viewmodel.UserManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gerenciar Usuários") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddDialog) {
                Icon(Icons.Default.Add, "Adicionar")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.users, key = { it.id }) { user ->
                UserItem(user = user)
            }
        }
    }

    if (state.showAddDialog) {
        AddUserDialog(
            name = state.newUserName,
            password = state.newUserPassword,
            area = state.newUserArea,
            permissionLevel = state.newUserPermissionLevel,
            allowedAreas = state.newUserAllowedAreas,
            error = state.error,
            onNameChange = viewModel::updateNewUserName,
            onPasswordChange = viewModel::updateNewUserPassword,
            onAreaChange = viewModel::updateNewUserArea,
            onPermissionLevelChange = viewModel::updateNewUserPermissionLevel,
            onToggleAllowedArea = viewModel::toggleAllowedArea,
            onConfirm = viewModel::addUser,
            onDismiss = viewModel::dismissAddDialog
        )
    }
}

@Composable
private fun UserItem(
    user: User,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${user.area.displayName} • ${user.permissionLevel.displayName}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AddUserDialog(
    name: String,
    password: String,
    area: Area,
    permissionLevel: PermissionLevel,
    allowedAreas: Set<Area>,
    error: String?,
    onNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAreaChange: (Area) -> Unit,
    onPermissionLevelChange: (PermissionLevel) -> Unit,
    onToggleAllowedArea: (Area) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Novo Usuário", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Senha") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(12.dp))

                Text("Área principal", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Area.entries.forEach { a ->
                        FilterChip(
                            selected = area == a,
                            onClick = { onAreaChange(a) },
                            label = { Text(a.displayName) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("Nível de permissão", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PermissionLevel.entries.forEach { p ->
                        FilterChip(
                            selected = permissionLevel == p,
                            onClick = { onPermissionLevelChange(p) },
                            label = { Text(p.displayName) }
                        )
                    }
                }

                if (permissionLevel == PermissionLevel.USER) {
                    Spacer(Modifier.height(12.dp))
                    Text("Áreas permitidas", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Area.entries.forEach { a ->
                            FilterChip(
                                selected = a in allowedAreas,
                                onClick = { onToggleAllowedArea(a) },
                                label = { Text(a.displayName) }
                            )
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm) {
                        Text("Adicionar")
                    }
                }
            }
        }
    }
}
