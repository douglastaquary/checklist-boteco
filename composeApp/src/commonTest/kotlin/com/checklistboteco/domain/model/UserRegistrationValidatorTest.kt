package com.checklistboteco.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserRegistrationValidatorTest {
    @Test
    fun normalizesFirstLetterOfEachName() {
        assertEquals("Maria Clara", UserRegistrationValidator.normalizeName("  mARIA   cLARA "))
    }

    @Test
    fun rejectsEmailWithoutAtOrDotCom() {
        val result = validInput(email = "maria@boteco.com.br").validate()

        assertTrue(result.isFailure)
    }

    @Test
    fun validatesStrongPasswordRules() {
        assertFalse(UserRegistrationValidator.isStrongPassword("senha"))
        assertFalse(UserRegistrationValidator.isStrongPassword("senhaforte1!"))
        assertFalse(UserRegistrationValidator.isStrongPassword("SenhaForte!"))
        assertFalse(UserRegistrationValidator.isStrongPassword("SenhaForte1"))
        assertTrue(UserRegistrationValidator.isStrongPassword("SENHA1!"))
        assertTrue(UserRegistrationValidator.isStrongPassword("Senha1!"))
        assertTrue(UserRegistrationValidator.isStrongPassword("SenhaForte1!"))
    }

    @Test
    fun mapsWorkSectorToAllowedActivityAreaForRegularUser() {
        val result = validInput(workSector = WorkSector.AJUDANTE_DE_COZINHA).validate()

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals(PermissionLevel.USER, user.permissionLevel)
        assertEquals(listOf(Area.COZINHA), user.allowedAreas)
    }

    @Test
    fun publicRegistrationDoesNotGrantFunctionalPermissions() {
        val result = validInput().validate().getOrThrow()

        assertEquals(PermissionLevel.USER, result.permissionLevel)
        assertFalse(result.featurePermissions.canRegisterUsers)
        assertFalse(result.featurePermissions.canCreateActivities)
        assertFalse(result.featurePermissions.canEditUsers)
    }

    private fun validInput(
        email: String = "maria@boteco.com",
        workSector: WorkSector = WorkSector.ATENDIMENTO
    ) = UserRegistrationInput(
        firstName = "maria",
        lastName = "silva",
        email = email,
        workSector = workSector,
        password = "SenhaForte1!",
        confirmPassword = "SenhaForte1!"
    )

    private fun UserRegistrationInput.validate() = UserRegistrationValidator.validate(this)
}
