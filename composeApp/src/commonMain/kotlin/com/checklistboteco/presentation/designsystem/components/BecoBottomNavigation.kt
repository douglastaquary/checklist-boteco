package com.checklistboteco.presentation.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.checklistboteco.presentation.designsystem.tokens.BecoSpacing
import com.checklistboteco.presentation.navigation.AppDestination

@Composable
fun BecoBottomNavigation(
    destinations: List<AppDestination>,
    selectedRoute: String?,
    hasOverflow: Boolean,
    onDestinationSelected: (AppDestination) -> Unit,
    onMoreSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(horizontal = BecoSpacing.md, vertical = BecoSpacing.xs),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(BecoSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(BecoSpacing.xs)
        ) {
            destinations.forEach { destination ->
                BecoNavigationItem(
                    icon = destination.icon,
                    label = destination.title,
                    contentDescription = destination.contentDescription,
                    selected = selectedRoute == destination.route,
                    onClick = { onDestinationSelected(destination) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (hasOverflow) {
                BecoNavigationItem(
                    icon = Icons.Default.MoreHoriz,
                    label = "Mais",
                    contentDescription = "Mais módulos",
                    selected = destinations.none { it.route == selectedRoute },
                    onClick = onMoreSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BecoNavigationItem(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(24.dp),
        color = container,
        contentColor = content
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = BecoSpacing.xs, vertical = BecoSpacing.xs)
        ) {
            Icon(icon, contentDescription, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
