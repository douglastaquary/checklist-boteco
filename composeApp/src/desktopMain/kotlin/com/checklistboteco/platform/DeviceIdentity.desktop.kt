package com.checklistboteco.platform

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

actual object DeviceIdentity {
    private val deviceFile: Path = Path.of(System.getProperty("user.home"), ".checklist-boteco", "device-id")

    actual fun getOrCreateDeviceId(): String {
        if (Files.exists(deviceFile)) return Files.readString(deviceFile).trim()
        Files.createDirectories(deviceFile.parent)
        val created = UUID.randomUUID().toString()
        Files.writeString(deviceFile, created)
        return created
    }

    actual fun deviceName(): String {
        return System.getProperty("os.name") + " " + System.getProperty("user.name")
    }
}
