package com.checklistboteco.domain.model

enum class Area(val displayName: String) {
    ATENDIMENTO("Atendimento"),
    COZINHA("Cozinha"),
    ESTOQUE("Estoque"),
    LIMPEZA("Limpeza");

    companion object {
        fun fromString(value: String): Area = entries.find { it.name == value } ?: ATENDIMENTO
    }
}
