package com.checklistboteco.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkSectorChecklistAccessTest {
    @Test
    fun kitchenSectorsSeeCozinhaChecklistOnly() {
        listOf(WorkSector.COZINHA, WorkSector.CHEFE_DE_COZINHA, WorkSector.AJUDANTE_DE_COZINHA).forEach { sector ->
            assertTrue(sector.isKitchenSector)
            assertEquals(listOf(Area.COZINHA), sector.checklistAreas)
        }
    }

    @Test
    fun nonKitchenSectorsSeeAtendimentoChecklist() {
        listOf(
            WorkSector.ATENDIMENTO,
            WorkSector.GARCON,
            WorkSector.CUMIM,
            WorkSector.GERENTE,
            WorkSector.ATENDENTE,
            WorkSector.BARMAN,
            WorkSector.SERVICOS_GERAIS,
        ).forEach { sector ->
            assertFalse(sector.isKitchenSector)
            assertEquals(listOf(Area.ATENDIMENTO), sector.checklistAreas)
        }
    }

    @Test
    fun userChecklistAccessibleAreasFollowsSectorRule() {
        val garcom = User(
            id = 1L,
            name = "Garçom",
            email = "g@test.com",
            password = "x",
            area = Area.ATENDIMENTO,
            workSector = WorkSector.GARCON,
            permissionLevel = PermissionLevel.USER,
            allowedAreas = listOf(Area.ATENDIMENTO),
        )
        assertEquals(listOf(Area.ATENDIMENTO), garcom.checklistAccessibleAreas)
        assertTrue(garcom.canAccessChecklistArea(Area.ATENDIMENTO))
        assertFalse(garcom.canAccessChecklistArea(Area.COZINHA))

        val ajudante = User(
            id = 2L,
            name = "Ajudante",
            email = "a@test.com",
            password = "x",
            area = Area.COZINHA,
            workSector = WorkSector.AJUDANTE_DE_COZINHA,
            permissionLevel = PermissionLevel.USER,
            allowedAreas = listOf(Area.COZINHA),
        )
        assertEquals(listOf(Area.COZINHA), ajudante.checklistAccessibleAreas)
        assertFalse(ajudante.canAccessChecklistArea(Area.ATENDIMENTO))
    }
}
