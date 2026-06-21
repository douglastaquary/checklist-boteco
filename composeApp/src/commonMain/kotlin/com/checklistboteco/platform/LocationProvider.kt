package com.checklistboteco.platform

import com.checklistboteco.domain.model.GeoPoint

data class LocationUpdate(
    val point: GeoPoint?,
    val accuracyMeters: Float?
)

expect object LocationProvider {
    fun initialize(platformContext: Any? = null)
    fun startUpdates(onUpdate: (LocationUpdate) -> Unit)
    fun stopUpdates()
    fun hasPermission(): Boolean
}
