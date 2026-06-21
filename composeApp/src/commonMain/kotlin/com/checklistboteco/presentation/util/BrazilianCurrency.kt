package com.checklistboteco.presentation.util

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

object BrazilianCurrency {
    fun formatCents(cents: Long): String {
        val safe = cents.coerceAtLeast(0)
        val reais = safe / 100
        val fraction = (safe % 100).toString().padStart(2, '0')
        return "R$ $reais,$fraction"
    }

    fun digitsToCents(digits: String): Long {
        val normalized = digits.filter(Char::isDigit)
        if (normalized.isBlank()) return 0L
        return normalized.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    }
}

@Composable
fun BrazilianCurrencyField(
    cents: Long,
    onCentsChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = BrazilianCurrency.formatCents(cents),
        onValueChange = { input ->
            onCentsChange(BrazilianCurrency.digitsToCents(input))
        },
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
