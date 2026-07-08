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

    val isKitchenSector: Boolean
        get() = this in setOf(COZINHA, CHEFE_DE_COZINHA, AJUDANTE_DE_COZINHA)

    /** Áreas visíveis no checklist: cozinha só vê COZINHA; demais setores veem ATENDIMENTO. */
    val checklistAreas: List<Area>
        get() = if (isKitchenSector) listOf(Area.COZINHA) else listOf(Area.ATENDIMENTO)

    companion object {
        fun fromString(value: String): WorkSector = entries.find { it.name == value } ?: ATENDIMENTO
    }
}
