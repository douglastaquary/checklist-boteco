package com.checklistboteco.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.checklistboteco.data.repository.GlobalDashboardStats
import com.checklistboteco.data.repository.UserRanking
import com.checklistboteco.presentation.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visão Geral", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    TextButton(onClick = {}) { Text("Hoje", color = Color.Gray) }
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Exportar", color = Color.White)
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.globalStats?.let { stats ->
                            SummaryCard(
                                title = "CONCLUSÃO",
                                value = "${stats.completionPercentage}%",
                                progress = stats.completionPercentage / 100f,
                                modifier = Modifier.weight(1f),
                                progressColor = Color(0xFF00C853)
                            )
                            AlertCard(
                                title = "ALERTAS",
                                count = stats.alertsCount,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.globalStats?.let { stats ->
                            SmallStatCard(label = "AGENDADOS", value = stats.scheduledCount.toString(), modifier = Modifier.weight(1f))
                            SmallStatCard(label = "EM TEMPO", value = stats.onTimeCount.toString(), color = Color(0xFF00C853), modifier = Modifier.weight(1f))
                            SmallStatCard(label = "ATRASADOS", value = stats.lateCount.toString(), color = Color(0xFFD50000), modifier = Modifier.weight(1f))
                        }
                    }
                }

                item {
                    RankingHeader()
                }

                items(state.ranking) { user ->
                    RankingRow(user)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, progress: Float, modifier: Modifier = Modifier, progressColor: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun AlertCard(title: String, count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(count.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Surface(
                color = if (count == 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    if (count == 0) "Tudo OK" else "Atenção",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (count == 0) Color(0xFF00C853) else Color(0xFFD50000)
                )
            }
        }
    }
}

@Composable
private fun SmallStatCard(label: String, value: String, color: Color = Color.Black, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun RankingHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF000033), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val headerStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("COLABORADOR", modifier = Modifier.weight(2f), style = headerStyle)
        Text("SCORE", modifier = Modifier.weight(1f), style = headerStyle)
        Text("PONTUAL", modifier = Modifier.weight(1f), style = headerStyle)
        Text("ESFORÇO", modifier = Modifier.weight(1f), style = headerStyle)
        Text("QUALID.", modifier = Modifier.weight(1f), style = headerStyle)
    }
}

@Composable
private fun RankingRow(user: UserRanking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFE0F2F1)), contentAlignment = Alignment.Center) {
                    Text(user.userName.take(1), color = Color(0xFF00C853), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(user.userName, fontSize = 14.sp)
            }
            Text("%.1f".format(user.score), modifier = Modifier.weight(1f), color = Color(0xFF00C853), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("${user.punctualPercentage.toInt()}%", modifier = Modifier.weight(1f), color = if (user.punctualPercentage < 50) Color.Red else Color.Black, fontSize = 12.sp)
            Text("${user.effortPercentage.toInt()}%", modifier = Modifier.weight(1f), fontSize = 12.sp)
            Text("${user.qualityPercentage.toInt()}%", modifier = Modifier.weight(1f), fontSize = 12.sp)
        }
    }
}
