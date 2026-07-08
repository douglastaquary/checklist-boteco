package com.checklistboteco.presentation.designsystem

import com.checklistboteco.domain.model.User

data class UserHeaderUiModel(
    val displayName: String,
    val roleLabel: String,
    val dateLabel: String,
    val initials: String
) {
    companion object {
        fun from(user: User, dateLabel: String): UserHeaderUiModel {
            val initials = user.name.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(2)
                .joinToString("") { it.first().uppercase() }.ifBlank { "CB" }
            return UserHeaderUiModel(
                displayName = user.name,
                roleLabel = user.workSector.displayName,
                dateLabel = dateLabel,
                initials = initials
            )
        }
    }
}
