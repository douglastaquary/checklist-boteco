package com.checklistboteco.platform

import com.checklistboteco.domain.model.GeoPoint
import com.checklistboteco.domain.model.WorksiteLocation

actual object LocationProvider {
    actual fun initialize(platformContext: Any?) = Unit

    actual fun hasPermission(): Boolean = true

    actual fun startUpdates(onUpdate: (LocationUpdate) -> Unit) {
        onUpdate(LocationUpdate(WorksiteLocation.point, 5f))
    }

    actual fun stopUpdates() = Unit
}
