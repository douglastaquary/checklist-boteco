package com.checklistboteco.database

import kotlin.Long
import kotlin.String

public data class CountActivitiesByArea(
  public val area: String,
  public val total: Long,
)
