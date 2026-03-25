package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StackedBarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.ActivityWithCompletion
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.platform.CameraCaptureTrigger
import com.checklistboteco.presentation.viewmodel.ChecklistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel,
    user: User,
    onLogout: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToActivitiesManagement: () -> Unit,
    onNavigateToUserManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val accessibleAreas = Area.entries.filter { user.canAccessArea(it) }

    CameraCaptureTrigger(
        trigger = state.showCameraForActivity != null,
        onImageCaptured = viewModel::onImageCaptured,
        onCancel = viewModel::onCameraCancel
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checklist - ${user.name}") },
                actions = {
                    if (user.permissionLevel == PermissionLevel.ADMIN) {
                        IconButton(onClick = onNavigateToAdmin) {
                            Icon(Icons.Default.StackedBarChart, "Relatórios")
                        }
                        IconButton(onClick = onNavigateToActivitiesManagement) {
                            Icon(Icons.Default.Settings, "Atividades")
                        }
                        IconButton(onClick = onNavigateToUserManagement) {
                            Icon(Icons.Default.People, "Usuários")
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Sair")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accessibleAreas.forEach { area ->
                    FilterChip(
                        selected = state.selectedArea == area,
                        onClick = { viewModel.selectArea(area) },
                        label = { Text(area.displayName) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(state.activities, key = { it.activity.id }) { item ->
                    ActivityItem(
                        activityWithCompletion = item,
                        onToggle = { viewModel.onActivityToggleClicked(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityItem(
    activityWithCompletion: ActivityWithCompletion,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (activity, isCompleted) = activityWithCompletion
    val backgroundColor = if (isCompleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = activity.area.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isCompleted,
                onCheckedChange = { if (!isCompleted) onToggle() },
                enabled = !isCompleted
            )
        }
    }
}
