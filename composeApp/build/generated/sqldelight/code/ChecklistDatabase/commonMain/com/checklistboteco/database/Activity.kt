package com.checklistboteco.database

import kotlin.Long
import kotlin.String

public data class Activity(
  public val id: Long,
  public val name: String,
  public val area: String,
  public val frequency: String,
)
