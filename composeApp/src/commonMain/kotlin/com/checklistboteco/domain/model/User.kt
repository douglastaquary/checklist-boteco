package com.checklistboteco.domain.model

data class User(
    val id: Long,
    val name: String,
    val password: String,
    val area: Area,
    val permissionLevel: PermissionLevel,
    val allowedAreas: List<Area>
) {
    fun canAccessArea(area: Area): Boolean {
        return permissionLevel == PermissionLevel.ADMIN || area in allowedAreas
    }
}
