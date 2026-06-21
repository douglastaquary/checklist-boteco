package com.checklistboteco.domain.model

data class FeaturePermissions(
    val canRegisterUsers: Boolean = false,
    val canCreateActivities: Boolean = false,
    val canEditUsers: Boolean = false,
    val canCreateInventoryCounts: Boolean = false,
    val canViewInventoryInsights: Boolean = false,
    val canManageAdministrativeStock: Boolean = false
) {
    companion object {
        val Admin = FeaturePermissions(
            canRegisterUsers = true,
            canCreateActivities = true,
            canEditUsers = true,
            canCreateInventoryCounts = true,
            canViewInventoryInsights = true,
            canManageAdministrativeStock = true
        )
    }
}
