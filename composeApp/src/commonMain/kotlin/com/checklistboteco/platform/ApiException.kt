package com.checklistboteco.platform

class ApiException(
    val userMessage: String,
    val httpStatus: Int? = null
) : Exception(userMessage)
