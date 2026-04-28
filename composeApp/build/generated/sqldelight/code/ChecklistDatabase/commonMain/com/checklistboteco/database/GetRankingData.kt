package com.checklistboteco.database

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class GetRankingData(
  public val name: String,
  public val totalCompletions: Long,
  public val onTimeCompletions: Long?,
  public val totalEffort: Double?,
)
