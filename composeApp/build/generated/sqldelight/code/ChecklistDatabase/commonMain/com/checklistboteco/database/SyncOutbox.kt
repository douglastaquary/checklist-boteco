package com.checklistboteco.database

import kotlin.Long
import kotlin.String

public data class SyncOutbox(
  public val operationId: String,
  public val entityType: String,
  public val entitySyncId: String,
  public val operationType: String,
  public val payload: String,
  public val createdAt: Long,
  public val attemptCount: Long,
  public val nextAttemptAt: Long,
  public val lastError: String?,
  public val status: String,
)
