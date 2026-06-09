package com.checklistboteco.platform

import android.content.Context
import android.os.Build
import java.util.UUID

actual object DeviceIdentity {
    private const val prefsName = "checklist_boteco_device"
    private const val deviceIdKey = "device_id"
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    actual fun getOrCreateDeviceId(): String {
        val appContext = context ?: return UUID.randomUUID().toString()
        val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val existing = prefs.getString(deviceIdKey, null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(deviceIdKey, created).apply()
        return created
    }

    actual fun deviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }
}
