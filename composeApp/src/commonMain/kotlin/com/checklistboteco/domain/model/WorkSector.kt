package com.checklistboteco.domain.model

enum class WorkSector(val displayName: String, val activityArea: Area) {
    ATENDIMENTO("Atendimento", Area.ATENDIMENTO),
    COZINHA("Cozinha", Area.COZINHA),
    SERVICOS_GERAIS("Serviços Gerais", Area.LIMPEZA),
    GARCON("Garçon", Area.ATENDIMENTO),
    CUMIM("Cumim", Area.ATENDIMENTO),
    CHEFE_DE_COZINHA("Chefe de Cozinha", Area.COZINHA),
    GERENTE("Gerente", Area.ATENDIMENTO),
    AJUDANTE_DE_COZINHA("Ajudante de Cozinha", Area.COZINHA),
    ATENDENTE("Atendente", Area.ATENDIMENTO),
    BARMAN("Barman", Area.ATENDIMENTO);

    companion object {
        fun fromString(value: String): WorkSector = entries.find { it.name == value } ?: ATENDIMENTO
    }
}
