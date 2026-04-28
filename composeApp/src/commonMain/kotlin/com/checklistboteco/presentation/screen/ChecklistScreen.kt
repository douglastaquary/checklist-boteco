package com.checklistboteco.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.ActivityWithCompletion
import com.checklistboteco.domain.model.User
import com.checklistboteco.platform.CameraCaptureTrigger
import com.checklistboteco.presentation.viewmodel.ChecklistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel,
    user: User,
    onLogout: () -> Unit,
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
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sair")
                    }
                }
            )
        },
        modifier = modifier
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
                contentPadding = PaddingValues(16.dp)
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
