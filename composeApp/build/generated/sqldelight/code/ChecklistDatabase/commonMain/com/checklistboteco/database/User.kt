package com.checklistboteco.database

import kotlin.Long
import kotlin.String

public data class User(
  public val id: Long,
  public val name: String,
  public val email: String,
  public val password: String,
  public val area: String,
  public val workSector: String,
  public val permissionLevel: String,
  public val allowedAreas: String,
  public val createdAt: Long,
  public val remoteId: String?,
  public val canRegisterUsers: Long,
  public val canCreateActivities: Long,
  public val canEditUsers: Long,
)
