package com.checklistboteco.domain.security

data class PasswordRuleResult(
    val message: String,
    val satisfied: Boolean
)

object PasswordPolicy {
    fun rules(password: String): List<PasswordRuleResult> {
        return listOf(
            PasswordRuleResult("Ao menos 6 caracteres", password.length >= 6),
            PasswordRuleResult("Ao menos uma letra maiúscula", password.any(Char::isUpperCase)),
            PasswordRuleResult("Ao menos um número", password.any(Char::isDigit)),
            PasswordRuleResult("Ao menos um caractere especial", password.any { !it.isLetterOrDigit() })
        )
    }

    fun isValid(password: String): Boolean = rules(password).all { it.satisfied }

    fun firstInvalidMessage(password: String): String? = rules(password).firstOrNull { !it.satisfied }?.message
}
