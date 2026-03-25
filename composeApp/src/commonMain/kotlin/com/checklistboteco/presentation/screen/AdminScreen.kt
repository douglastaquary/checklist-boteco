package com.checklistboteco.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.checklistboteco.domain.model.Area
import com.checklistboteco.presentation.viewmodel.AdminViewModel
import com.checklistboteco.presentation.viewmodel.AreaStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatórios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Atividades por Área",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Area.entries.forEach { area ->
                val stats = state.statsByArea[area] ?: AreaStats(area, 0, 0, 0)
                StatsCard(stats = stats)
            }
        }
    }
}

@Composable
private fun StatsCard(
    stats: AreaStats,
    modifier: Modifier = Modifier
) {
    val total = stats.total
    val completed = stats.completed
    val pending = stats.pending
    val progress = if (total > 0) completed.toFloat() / total else 0f
    
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stats.area.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "$completed / $total realizadas",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .padding(vertical = 4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxWidth()) {
                    val barHeight = size.height
                    val barWidth = size.width
                    drawRect(
                        color = backgroundColor,
                        topLeft = Offset.Zero,
                        size = Size(barWidth, barHeight)
                    )
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset.Zero,
                        size = Size(barWidth * progress, barHeight)
                    )
                }
            }
            Text(
                "Pendentes: $pending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
