package com.ivan.finanzapp.ui.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.components.formatCOP
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.ZoneId

class AutonomiaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val categoryDao = entryPoint.categoryDao()
        val transactionDao = entryPoint.transactionDao()

        val today = LocalDate.now()
        val monthStart = LocalDate.of(today.year, today.month, 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val monthEnd = LocalDate.now().plusMonths(1).withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Calcular presupuesto total
        val categories = categoryDao.getAllSnapshot()
        var totalBudget = categories.sumOf { it.budgetLimit ?: 0.0 }

        // Calcular gastos
        val transactions = transactionDao.getByDateRangeSnapshot(monthStart, monthEnd)
        val monthlySpent = transactions.filter {
            it.type == TransactionType.GASTO || it.type == TransactionType.GASTO_TC
        }.sumOf { it.amount }

        // Si no hay presupuesto establecido en categorías, usamos el ingreso como presupuesto estimado
        if (totalBudget <= 0.0) {
            totalBudget = transactions.filter {
                it.type == TransactionType.INGRESO
            }.sumOf { it.amount }
        }

        val hasMonthlyBase = totalBudget > 0.0
        val margin = totalBudget - monthlySpent
        val remaining = margin.coerceAtLeast(0.0)
        val percentage = if (hasMonthlyBase) {
            ((remaining / totalBudget) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val statusColor = when {
            !hasMonthlyBase -> FinanzWidgetColors.TextMutedColor
            margin < 0.0 -> FinanzWidgetColors.ErrorColor
            percentage > 50 -> FinanzWidgetColors.SuccessColor
            percentage > 20 -> FinanzWidgetColors.WarningColor
            else -> FinanzWidgetColors.ErrorColor
        }
        val headlineText = when {
            !hasMonthlyBase -> "Sin meta"
            margin < 0.0 -> "Mes excedido"
            else -> "$percentage% disponible"
        }
        val detailText = when {
            !hasMonthlyBase -> "Registra ingresos"
            margin < 0.0 -> "Faltan ${formatCOP(-margin)}"
            else -> "Quedan ${formatCOP(remaining)}"
        }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(FinanzWidgetColors.Background)
                    .cornerRadius(16.dp)
                    .padding(12.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "MARGEN DEL MES",
                        style = TextStyle(
                            color = FinanzWidgetColors.TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = headlineText,
                        style = TextStyle(
                            color = FinanzWidgetColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = detailText,
                        style = TextStyle(
                            color = ColorProvider(statusColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
