package com.checklistboteco.domain.model

enum class PermissionLevel(val displayName: String) {
    ADMIN("Administrador"),
    USER("Usuário");

    companion object {
        fun fromString(value: String): PermissionLevel = entries.find { it.name == value } ?: USER
    }
}
