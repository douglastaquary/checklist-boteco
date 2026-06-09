package com.checklistboteco.domain.model

data class FeaturePermissions(
    val canRegisterUsers: Boolean = false,
    val canCreateActivities: Boolean = false,
    val canEditUsers: Boolean = false
) {
    companion object {
        val Admin = FeaturePermissions(
            canRegisterUsers = true,
            canCreateActivities = true,
            canEditUsers = true
        )
    }
}
