package com.checklistboteco.platform

import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice

actual object DeviceIdentity {
    private const val deviceIdKey = "checklist_boteco_device_id"

    actual fun getOrCreateDeviceId(): String {
        val defaults = NSUserDefaults.standardUserDefaults
        val existing = defaults.stringForKey(deviceIdKey)
        if (existing != null) return existing
        val created = NSUUID.UUID().UUIDString
        defaults.setObject(created, deviceIdKey)
        return created
    }

    actual fun deviceName(): String {
        return UIDevice.currentDevice.name
    }
}
