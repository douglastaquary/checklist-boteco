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
    val remoteId: String? = null,
    val featurePermissions: FeaturePermissions = FeaturePermissions()
) {
    fun canAccessArea(area: Area): Boolean {
        return permissionLevel == PermissionLevel.ADMIN || area in allowedAreas
    }

    /** Áreas do checklist derivadas do setor (admin vê todas). */
    val checklistAccessibleAreas: List<Area>
        get() = if (permissionLevel == PermissionLevel.ADMIN) {
            Area.entries.toList()
        } else {
            workSector.checklistAreas
        }

    fun canAccessChecklistArea(area: Area): Boolean = area in checklistAccessibleAreas

    fun canRegisterUsers(): Boolean {
        return permissionLevel == PermissionLevel.ADMIN || featurePermissions.canRegisterUsers
    }

    fun canCreateActivities(): Boolean {
        return permissionLevel == PermissionLevel.ADMIN || featurePermissions.canCreateActivities
    }

    fun canEditUsers(): Boolean {
        return permissionLevel == PermissionLevel.ADMIN || featurePermissions.canEditUsers
    }

    fun canCreateInventoryCounts() = permissionLevel == PermissionLevel.ADMIN || featurePermissions.canCreateInventoryCounts
    fun canViewInventoryInsights() = permissionLevel == PermissionLevel.ADMIN || featurePermissions.canViewInventoryInsights
    fun canManageAdministrativeStock() = permissionLevel == PermissionLevel.ADMIN || featurePermissions.canManageAdministrativeStock

    fun canManagePermissions(): Boolean {
        return permissionLevel == PermissionLevel.ADMIN
    }

    fun canUseWorkClock(): Boolean = permissionLevel != PermissionLevel.ADMIN

    fun canUseInventoryModule(): Boolean {
        return canCreateInventoryCounts() || canViewInventoryInsights() || canManageAdministrativeStock()
    }

    fun canUseDashboardModule(): Boolean {
        return canCreateActivities() || canEditUsers() || canRegisterUsers()
    }

    fun canUseActivitiesModule(): Boolean = canCreateActivities()
}
