package com.checklistboteco.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.WorkClockCalculator
import com.checklistboteco.domain.model.WorkClockEntry
import com.checklistboteco.domain.model.WorksiteLocation
import com.checklistboteco.presentation.viewmodel.WorkClockViewModel
import com.checklistboteco.presentation.designsystem.components.BecoBackButton
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkClockScreen(
    viewModel: WorkClockViewModel,
    user: User,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    WorkClockLocationEffect(viewModel)

    if (state.showDetails) {
        WorkClockDetailsScreen(
            entries = state.entries,
            summary = state.summary,
            onBack = viewModel::hideDetails,
            modifier = modifier
        )
        return
    }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::showDetails,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ver marcações do dia")
                }
                Button(
                    onClick = viewModel::confirmClockIn,
                    enabled = state.canClockIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirmar ${state.nextType.displayName}")
                }
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ponto", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Próxima marcação: ${state.nextType.displayName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                WorkMap(
                    distanceMeters = state.distanceFromWorkMeters,
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
            item {
                WorkClockInfoSection("Agora") {
                    InfoRow("Colaborador", user.name)
                    InfoRow("Dia e hora", formatDateTime(state.currentTimestamp))
                    InfoRow("Local", WorksiteLocation.address)
                    InfoRow("Distância", if (state.distanceFromWorkMeters == Double.MAX_VALUE) "—" else "${state.distanceFromWorkMeters.toInt()} m")
                    InfoRow("GPS", state.locationStatus)
                }
            }
            item {
                WorkClockInfoSection("Resumo") {
                    InfoRow("Trabalhadas hoje", WorkClockCalculator.formatDuration(state.summary.workedMillis))
                    InfoRow("Extras na semana", WorkClockCalculator.formatDuration(state.summary.overtimeMillis))
                    InfoRow("Descanso devido", WorkClockCalculator.formatDuration(state.summary.missingBreakMillis))
                    InfoRow("Horas devidas hoje", WorkClockCalculator.formatDuration(state.summary.missingDailyMillis))
                }
            }
            if (state.isAdmin || !state.canClockIn) {
                item {
                    Text(
                        if (state.isAdmin) "Usuários admin não têm acesso à funcionalidade de ponto." else state.locationStatus,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    state.feedback?.let { feedback ->
        AlertDialog(
            onDismissRequest = viewModel::dismissFeedback,
            title = { Text("Ponto") },
            text = { Text(feedback) },
            confirmButton = {
                Button(onClick = viewModel::dismissFeedback) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun WorkClockInfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun WorkMap(
    distanceMeters: Double,
    modifier: Modifier = Modifier
) {
    val roadColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val userColor = if (distanceMeters <= WorksiteLocation.allowedRadiusMeters) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = roadColor,
                start = Offset(0f, size.height * 0.32f),
                end = Offset(size.width, size.height * 0.72f),
                strokeWidth = 18f
            )
            drawLine(
                color = roadColor,
                start = Offset(size.width * 0.2f, 0f),
                end = Offset(size.width * 0.68f, size.height),
                strokeWidth = 12f
            )
            drawCircle(
                color = Color.White,
                radius = 34f,
                center = Offset(size.width * 0.52f, size.height * 0.48f)
            )
            drawCircle(
                color = userColor,
                radius = 22f,
                center = Offset(size.width * 0.52f, size.height * 0.48f)
            )
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Place, null)
                Column {
                    Text(WorksiteLocation.name, style = MaterialTheme.typography.titleSmall)
                    Text("${distanceMeters.toInt()} m do local", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkClockDetailsScreen(
    entries: List<WorkClockEntry>,
    summary: com.checklistboteco.domain.model.WorkClockSummary,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marcações do dia") },
                navigationIcon = {
                    BecoBackButton(onClick = onBack)
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SummaryCard(summary = summary)
            }
            items(entries, key = { it.id }) { entry ->
                ClockEntryCard(entry)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: com.checklistboteco.domain.model.WorkClockSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InfoRow("Trabalhadas", WorkClockCalculator.formatDuration(summary.workedMillis))
            InfoRow("Almoço", WorkClockCalculator.formatDuration(summary.lunchMillis))
            InfoRow("Descanso", WorkClockCalculator.formatDuration(summary.restMillis))
            InfoRow("Descanso necessário", WorkClockCalculator.formatDuration(summary.requiredBreakMillis))
            InfoRow("Descanso devido", WorkClockCalculator.formatDuration(summary.missingBreakMillis))
            InfoRow("Descanso excedente", WorkClockCalculator.formatDuration(summary.breakOverageMillis))
            InfoRow("Horas devidas hoje", WorkClockCalculator.formatDuration(summary.missingDailyMillis))
            InfoRow("Horas devidas na semana", WorkClockCalculator.formatDuration(summary.missingWeeklyMillis))
            InfoRow("Horas extras (semana)", WorkClockCalculator.formatDuration(summary.overtimeMillis))
            if (summary.requiresTwoHoursRest) {
                Text("Jornada de 12h requer 2h de descanso.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ClockEntryCard(entry: WorkClockEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, null)
                Column {
                    Text(entry.type.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(formatDateTime(entry.registeredAt), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (entry.isLate) {
                Text("Atraso", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDateTime(timestamp: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
    val date = dateTime.date
    val time = dateTime.time
    return "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthNumber.toString().padStart(2, '0')}/${date.year} " +
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}:${time.second.toString().padStart(2, '0')}"
}
