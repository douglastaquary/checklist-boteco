package com.checklistboteco.presentation.designsystem.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.checklistboteco.presentation.designsystem.tokens.BecoSpacing

data class BecoFilterOption<T>(val value: T, val label: String, val count: Int? = null)

@Composable
fun <T> BecoSegmentedFilter(
    options: List<BecoFilterOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(BecoSpacing.xs)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.value == selected,
                onClick = { onSelected(option.value) },
                label = {
                    Row(horizontalArrangement = Arrangement.spacedBy(BecoSpacing.xs)) {
                        Text(option.label)
                        option.count?.let { Badge { Text(it.toString()) } }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                    selectedLabelColor = MaterialTheme.colorScheme.background
                )
            )
        }
    }
}
