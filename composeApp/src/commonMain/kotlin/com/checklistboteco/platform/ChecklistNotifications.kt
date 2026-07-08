package com.checklistboteco.platform

import androidx.compose.runtime.Composable
import com.checklistboteco.domain.model.ActivityWithCompletion
import com.checklistboteco.domain.model.ChecklistSchedule

@Composable
expect fun ChecklistNotificationEffect(items: List<ActivityWithCompletion>, userRemoteId: String?, schedule: ChecklistSchedule)
