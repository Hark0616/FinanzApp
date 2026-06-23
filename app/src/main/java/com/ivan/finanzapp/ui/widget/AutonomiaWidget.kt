package com.ivan.finanzapp.ui.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
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
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
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

        val remaining = (totalBudget - monthlySpent).coerceAtLeast(0.0)
        val percentage = if (totalBudget > 0.0) {
            ((remaining / totalBudget) * 100).toInt().coerceIn(0, 100)
        } else {
            100
        }

        val progressColor = if (percentage > 50) Color(0xFF81C784) else if (percentage > 20) Color(0xFFFFD54F) else Color(0xFFE57373)

        val widgetBgColor = ColorProvider(Color(0xFF1E1E1E))
        val textPrimaryColor = ColorProvider(Color(0xFFF5F6F8))
        val textSecondaryColor = ColorProvider(Color(0xFF8E95A5))

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(widgetBgColor)
                    .cornerRadius(16.dp)
                    .padding(12.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "AUTONOMÍA MENSUAL",
                        style = TextStyle(
                            color = textSecondaryColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "$percentage% restante",
                        style = TextStyle(
                            color = textPrimaryColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Quedan ${formatCOP(remaining)}",
                        style = TextStyle(
                            color = ColorProvider(progressColor),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
