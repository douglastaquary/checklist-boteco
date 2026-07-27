package com.checklistboteco.receipt

/**
 * iOS OCR uses Vision in PurchasesFeature.
 * This framework exports ReceiptProcessor for Swift to call parse/classify/session APIs.
 */
object IosReceiptProcessorBridge {
    fun parse(ocrText: String): ReceiptScan = ReceiptProcessor.parseReceipt(ocrText)
}
