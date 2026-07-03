package com.checklistboteco.presentation.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.checklistboteco.presentation.designsystem.UserHeaderUiModel
import com.checklistboteco.presentation.designsystem.tokens.BecoSize
import com.checklistboteco.presentation.designsystem.tokens.BecoSpacing

@Composable
fun BecoUserHeader(
    model: UserHeaderUiModel,
    onSearch: (() -> Unit)? = null,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = BecoSpacing.md, vertical = BecoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(BecoSize.avatar)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .semantics { contentDescription = "Iniciais de ${model.displayName}" },
            contentAlignment = Alignment.Center
        ) {
            Text(model.initials, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(BecoSpacing.sm))
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = "${model.displayName}, ${model.roleLabel}, ${model.dateLabel}"
            }
        ) {
            Text("Olá, ${model.displayName}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text("${model.roleLabel} · ${model.dateLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Buscar") }
        Box {
            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreHoriz, "Mais opções") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Sair") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                    onClick = { menuExpanded = false; onLogout() }
                )
            }
        }
    }
}
