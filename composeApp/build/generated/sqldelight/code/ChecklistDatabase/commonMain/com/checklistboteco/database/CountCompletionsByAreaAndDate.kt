package com.checklistboteco.database

import kotlin.Long
import kotlin.String

public data class CountCompletionsByAreaAndDate(
  public val area: String,
  public val completed: Long,
)
