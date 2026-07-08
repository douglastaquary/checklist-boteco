package com.checklistboteco.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.presentation.viewmodel.PermissionManagementViewModel
import com.checklistboteco.presentation.designsystem.components.BecoBackButton
import com.checklistboteco.presentation.designsystem.components.BecoPageHeader
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagementScreen(
    viewModel: PermissionManagementViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val usersBySector = state.users.groupBy { it.workSector }

    state.selectedUser?.let { user ->
        UserPermissionDetailsScreen(
            user = user,
            error = state.error,
            onBack = viewModel::closeDetails,
            onCanRegisterUsersChange = viewModel::updateCanRegisterUsers,
            onCanCreateActivitiesChange = viewModel::updateCanCreateActivities,
            onCanEditUsersChange = viewModel::updateCanEditUsers,
            onCanCreateInventoryCountsChange = viewModel::updateCanCreateInventoryCounts,
            onCanViewInventoryInsightsChange = viewModel::updateCanViewInventoryInsights,
            onCanManageAdministrativeStockChange = viewModel::updateCanManageAdministrativeStock,
            modifier = modifier
        )
        return
    }

    Scaffold(
        topBar = {
            BecoPageHeader(title = "Equipe", subtitle = "Usuários e permissões de acesso")
        },
        modifier = modifier
    ) { padding ->
        if (!state.canManagePermissions) {
            Text(
                "Somente administradores podem acessar este módulo",
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                usersBySector.forEach { (sector, users) ->
                    item {
                        Text(
                            sector.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(users, key = { it.id }) { user ->
                        UserPermissionItem(
                            user = user,
                            onClick = { viewModel.selectUser(user) }
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun UserPermissionItem(
    user: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
                    "${user.workSector.displayName} • ${user.permissionLevel.displayName}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserPermissionDetailsScreen(
    user: User,
    error: String?,
    onBack: () -> Unit,
    onCanRegisterUsersChange: (Boolean) -> Unit,
    onCanCreateActivitiesChange: (Boolean) -> Unit,
    onCanEditUsersChange: (Boolean) -> Unit,
    onCanCreateInventoryCountsChange: (Boolean) -> Unit,
    onCanViewInventoryInsightsChange: (Boolean) -> Unit,
    onCanManageAdministrativeStockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isAdmin = user.permissionLevel == PermissionLevel.ADMIN

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissões de usuário") },
                navigationIcon = {
                    BecoBackButton(onClick = onBack)
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(user.name, style = MaterialTheme.typography.headlineSmall)
            Text("Email: ${user.email.ifBlank { "Não informado" }}")
            Text("Setor: ${user.workSector.displayName}")
            Text("Área de atividades: ${user.area.displayName}")
            Text("Perfil: ${user.permissionLevel.displayName}")
            Text("Criado em: ${formatCreatedAt(user.createdAt)}")

            Text(
                "Funcionalidades",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp)
            )

            PermissionToggle(
                label = "Cadastro de novos funcionários",
                checked = user.canRegisterUsers(),
                enabled = !isAdmin,
                onCheckedChange = onCanRegisterUsersChange
            )
            PermissionToggle(
                label = "Criar novas atividades",
                checked = user.canCreateActivities(),
                enabled = !isAdmin,
                onCheckedChange = onCanCreateActivitiesChange
            )
            PermissionToggle(
                label = "Editar usuários",
                checked = user.canEditUsers(),
                enabled = !isAdmin,
                onCheckedChange = onCanEditUsersChange
            )
            PermissionToggle(label="Criar contagens de mercadorias",checked=user.canCreateInventoryCounts(),enabled=!isAdmin,onCheckedChange=onCanCreateInventoryCountsChange)
            PermissionToggle(label="Visualizar insights e auditoria",checked=user.canViewInventoryInsights(),enabled=!isAdmin,onCheckedChange=onCanViewInventoryInsightsChange)
            PermissionToggle(label="Contagem administrativa de estoque",checked=user.canManageAdministrativeStock(),enabled=!isAdmin,onCheckedChange=onCanManageAdministrativeStockChange)

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PermissionToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

private fun formatCreatedAt(value: Long): String {
    if (value <= 0L) return "Não informado"
    val date = Instant.fromEpochMilliseconds(value).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthNumber.toString().padStart(2, '0')}/${date.year}"
}
