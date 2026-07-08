package com.checklistboteco.presentation

import com.checklistboteco.domain.model.*
import com.checklistboteco.presentation.designsystem.UserHeaderUiModel
import com.checklistboteco.presentation.navigation.AppDestination
import com.checklistboteco.presentation.screen.ChecklistFilter
import com.checklistboteco.presentation.screen.filterChecklistItems
import kotlin.test.*

class DesignSystemPresentationTest {
    @Test
    fun regularUserKeepsCompactNavigation() {
        val layout = AppDestination.layoutFor(user())
        assertEquals(listOf(AppDestination.Checklist, AppDestination.WorkClock), layout.primary)
        assertTrue(layout.overflow.isEmpty())
    }

    @Test
    fun adminNavigationMovesExcessDestinationsToMore() {
        val layout = AppDestination.layoutFor(user(level = PermissionLevel.ADMIN, permissions = FeaturePermissions.Admin))
        assertTrue(layout.primary.size <= 3)
        assertTrue(layout.overflow.isNotEmpty())
        assertEquals(AppDestination.Checklist, layout.primary.first())
        assertEquals(AppDestination.Permissions, layout.overflow.last())
    }

    @Test
    fun headerUsesNameRoleDateAndTwoInitials() {
        val model = UserHeaderUiModel.from(user(name = "Maria Clara", sector = WorkSector.GERENTE), "02/07/2026")
        assertEquals("Maria Clara", model.displayName)
        assertEquals("Gerente", model.roleLabel)
        assertEquals("02/07/2026", model.dateLabel)
        assertEquals("MC", model.initials)
    }

    @Test
    fun checklistFiltersPendingAndCompletedWithoutChangingOrder() {
        val pending = item(1, false)
        val completed = item(2, true)
        val values = listOf(pending, completed)
        assertEquals(listOf(pending), filterChecklistItems(values, ChecklistFilter.PENDING))
        assertEquals(listOf(completed), filterChecklistItems(values, ChecklistFilter.COMPLETED))
        assertEquals(values, filterChecklistItems(values, ChecklistFilter.ALL))
    }

    private fun user(
        name: String = "João Silva",
        sector: WorkSector = WorkSector.ATENDIMENTO,
        level: PermissionLevel = PermissionLevel.USER,
        permissions: FeaturePermissions = FeaturePermissions.Default
    ) = User(
        id = 1,
        name = name,
        email = "user@boteco.com",
        password = "",
        area = sector.activityArea,
        workSector = sector,
        permissionLevel = level,
        allowedAreas = listOf(sector.activityArea),
        featurePermissions = permissions
    )

    private fun item(id: Long, completed: Boolean) = ActivityWithCompletion(
        activity = Activity(id, "activity-$id", "Atividade $id", Area.ATENDIMENTO, Frequency.DIARIO),
        isCompleted = completed
    )
}
