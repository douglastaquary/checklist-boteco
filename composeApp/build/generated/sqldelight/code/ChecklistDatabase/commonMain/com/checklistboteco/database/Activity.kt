package com.checklistboteco.database

import kotlin.Long
import kotlin.String

public data class Activity(
  public val id: Long,
  public val syncId: String?,
  public val name: String,
  public val area: String,
  public val frequency: String,
  public val effort: Long,
  public val serverRevision: Long,
  public val syncState: String,
  public val deletedAt: Long?,
)
