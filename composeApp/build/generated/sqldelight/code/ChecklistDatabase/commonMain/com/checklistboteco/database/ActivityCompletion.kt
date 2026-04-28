package com.checklistboteco.database

import kotlin.Long
import kotlin.String

public data class ActivityCompletion(
  public val id: Long,
  public val activityId: Long,
  public val userId: Long,
  public val completedAt: Long,
  public val imagePath: String?,
  public val isLate: Long,
)
