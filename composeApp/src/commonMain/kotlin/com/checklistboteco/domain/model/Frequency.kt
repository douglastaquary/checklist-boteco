package com.checklistboteco.domain.model

enum class Frequency(val displayName: String) {
    DIARIO("Diário"),
    QUINZENAL("Quinzenal"),
    MENSAL("Mensal");

    companion object {
        fun fromString(value: String): Frequency = entries.find { it.name == value } ?: DIARIO
    }
}
