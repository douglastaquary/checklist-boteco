package com.checklistboteco.database

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class WorkClockEntry(
  public val id: Long,
  public val userId: Long,
  public val type: String,
  public val registeredAt: Long,
  public val latitude: Double,
  public val longitude: Double,
  public val distanceFromWorkMeters: Double,
  public val isLate: Long,
)
