package com.checklistboteco.platform

expect object DeviceIdentity {
    fun getOrCreateDeviceId(): String
    fun deviceName(): String
}
