package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.data.remote.RemoteInventoryAudit
import com.checklistboteco.data.remote.RemoteInventoryAuditItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldSkipCsvUploadTest {
  @Test
  fun skipsWhenTotalSoldPositive() {
    val audit = RemoteInventoryAudit(
      date = "2026-06-20",
      location = "Beco",
      totalSold = 1.0
    )
    assertTrue(shouldSkipCsvUpload(audit))
  }

  @Test
  fun skipsWhenAnyItemHasSoldQuantity() {
    val audit = RemoteInventoryAudit(
      date = "2026-06-20",
      location = "Beco",
      items = listOf(
        RemoteInventoryAuditItem(
          product = "Cerveja",
          status = "OK",
          notes = "",
          soldQuantity = 2.0
        )
      )
    )
    assertTrue(shouldSkipCsvUpload(audit))
  }

  @Test
  fun requiresUploadWhenNoSales() {
    val audit = RemoteInventoryAudit(
      date = "2026-06-20",
      location = "Beco",
      items = listOf(
        RemoteInventoryAuditItem(
          product = "Cerveja",
          status = "OK",
          notes = ""
        )
      )
    )
    assertFalse(shouldSkipCsvUpload(audit))
  }
}
