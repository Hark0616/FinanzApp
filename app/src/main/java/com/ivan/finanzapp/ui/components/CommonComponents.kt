package com.ivan.finanzapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Muestra un monto en COP con color diferenciado:
 * verde para ingresos, rojo para gastos.
 *
 * @param amount valor del monto (siempre positivo; [isIncome] determina el color)
 * @param isIncome true → verde con "+" , false → rojo con "-"
 */
@Composable
fun AmountText(
    amount: Double,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
    fontSize: Int = 16
) {
    val prefix = if (isIncome) "+" else "-"
    val color = if (isIncome) Color(0xFF2E7D32) else Color(0xFFC62828)
    Text(
        text = "$prefix${formatCOP(amount)}",
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize.sp,
        modifier = modifier
    )
}

/**
 * Chip de categoría (pequeña etiqueta coloreada).
 *
 * @param name nombre de la categoría
 * @param colorHex color hex de la categoría, ej "#4CAF50"
 */
@Composable
fun CategoryChip(
    name: String,
    colorHex: String,
    modifier: Modifier = Modifier
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(Color.Gray)

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = name,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Encabezado de sección (título + línea divisoria opcional).
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(bottom = 8.dp)
    )
}
