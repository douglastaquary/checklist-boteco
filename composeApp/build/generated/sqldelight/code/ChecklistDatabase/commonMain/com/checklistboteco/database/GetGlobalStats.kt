package com.checklistboteco.database

import kotlin.Long

public data class GetGlobalStats(
  public val totalActivities: Long,
  public val totalCompleted: Long,
  public val lateCompletions: Long,
)
