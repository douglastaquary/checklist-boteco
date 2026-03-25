package com.checklistboteco.database

import kotlin.Long
import kotlin.String

public data class User(
  public val id: Long,
  public val name: String,
  public val password: String,
  public val area: String,
  public val permissionLevel: String,
  public val allowedAreas: String,
)
