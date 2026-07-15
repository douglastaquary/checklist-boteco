package com.checklistboteco.domain.model

data class UserRegistrationInput(
    val firstName: String,
    val lastName: String,
    val email: String,
    val workSector: WorkSector,
    val password: String,
    val confirmPassword: String
)

data class ValidatedUserRegistration(
    val fullName: String,
    val email: String,
    val workSector: WorkSector,
    val password: String,
    val permissionLevel: PermissionLevel,
    val allowedAreas: List<Area>,
    val featurePermissions: FeaturePermissions
)

object UserRegistrationValidator {
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.com$", RegexOption.IGNORE_CASE)
    private val specialCharacterRegex = Regex("[^A-Za-z0-9]")

    fun validate(input: UserRegistrationInput): Result<ValidatedUserRegistration> {
        val firstName = normalizeName(input.firstName)
        val lastName = normalizeName(input.lastName)
        val email = input.email.trim().lowercase()

        if (firstName.isBlank()) return Result.failure(IllegalArgumentException("Digite o nome"))
        if (lastName.isBlank()) return Result.failure(IllegalArgumentException("Digite o sobrenome"))
        if (!emailRegex.matches(email)) return Result.failure(IllegalArgumentException("Digite um email válido com @ e .com"))
        if (!isStrongPassword(input.password)) {
            return Result.failure(IllegalArgumentException("A senha deve ter ao menos 6 caracteres, maiúscula, número e caractere especial"))
        }
        if (input.password != input.confirmPassword) {
            return Result.failure(IllegalArgumentException("A confirmação de senha não confere"))
        }

        return Result.success(
            ValidatedUserRegistration(
                fullName = "$firstName $lastName",
                email = email,
                workSector = input.workSector,
                password = input.password,
                permissionLevel = PermissionLevel.USER,
                allowedAreas = listOf(input.workSector.activityArea),
                featurePermissions = FeaturePermissions()
            )
        )
    }

    fun normalizeName(value: String): String {
        return value.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { char -> char.uppercase() }
            }
    }

    fun isStrongPassword(value: String): Boolean {
        return value.length >= 6 &&
            value.any { it.isUpperCase() } &&
            value.any { it.isDigit() } &&
            specialCharacterRegex.containsMatchIn(value)
    }
}
