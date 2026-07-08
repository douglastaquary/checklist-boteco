package com.checklistboteco.presentation.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.checklistboteco.presentation.designsystem.tokens.BecoSize
import com.checklistboteco.presentation.designsystem.tokens.BecoSpacing
import com.checklistboteco.domain.model.ChecklistTimingStatus

@Composable
fun BecoTaskSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BecoSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = BecoSpacing.sm))
        content()
    }
}

@Composable
fun BecoTaskRow(
    title: String,
    metadata: String,
    trailingLabel: String? = null,
    completed: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    timingStatus: ChecklistTimingStatus? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, timingColor(timingStatus), RoundedCornerShape(12.dp))
            .semantics { stateDescription = timingStatus?.statusDescription ?: if (completed) "Concluída" else "Pendente" }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = BecoSpacing.sm, vertical = BecoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = completed,
            onCheckedChange = { if (!completed && enabled) onClick() },
            enabled = enabled,
            modifier = Modifier.size(BecoSize.minTouchTarget)
        )
        Spacer(Modifier.width(BecoSpacing.xs))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
                color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(metadata, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailingLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun timingColor(status: ChecklistTimingStatus?): Color = when (status) {
    ChecklistTimingStatus.GREEN -> Color(0xFF2E7D32)
    ChecklistTimingStatus.YELLOW -> Color(0xFFF9A825)
    ChecklistTimingStatus.RED -> Color(0xFFC62828)
    ChecklistTimingStatus.COMPLETED, null -> Color.Transparent
}

private val ChecklistTimingStatus.statusDescription: String get() = when (this) {
    ChecklistTimingStatus.GREEN -> "Dentro do prazo"
    ChecklistTimingStatus.YELLOW -> "Próxima do limite"
    ChecklistTimingStatus.RED -> "Atrasada"
    ChecklistTimingStatus.COMPLETED -> "Concluída"
}
