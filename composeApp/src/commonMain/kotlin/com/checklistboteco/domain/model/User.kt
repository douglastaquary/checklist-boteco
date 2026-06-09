package com.checklistboteco.domain.model

data class User(
    val id: Long,
    val name: String,
    val email: String = "",
    val password: String,
    val area: Area,
    val workSector: WorkSector = WorkSector.ATENDIMENTO,
    val permissionLevel: PermissionLevel,
    val allowedAreas: List<Area>,
    val createdAt: Long = 0L,
    val featurePermissions: FeaturePermissions = FeaturePermissions()
) {
    fun canAccessArea(area: Area): Boolean {
        return permissionLevel == PermissionLevel.ADMIN || area in allowedAreas
    }

    fun canRegisterUsers(): Boolean {
        return permissionLevel == PermissionLevel.ADMIN || featurePermissions.canRegisterUsers
    }

    fun canCreateActivities(): Boolean {
        return permissionLevel == PermissionLevel.ADMIN || featurePermissions.canCreateActivities
    }

    fun canEditUsers(): Boolean {
        return permissionLevel == PermissionLevel.ADMIN || featurePermissions.canEditUsers
    }

    fun canManagePermissions(): Boolean {
        return permissionLevel == PermissionLevel.ADMIN
    }
}
