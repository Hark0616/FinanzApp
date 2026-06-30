package com.ivan.finanzapp.ui.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ivan.finanzapp.MainActivity
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.components.formatCOP
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class UltimosMovimientosWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val transactionDao = entryPoint.transactionDao()
        val categoryDao = entryPoint.categoryDao()

        // Fetch top 4 transactions and categories snapshot asynchronously & optimized
        val recentTransactions = transactionDao.getRecentTransactionsSnapshot(4)
        val categories = categoryDao.getAllSnapshot()
        val categoryMap = categories.associateBy { it.id }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(FinanzWidgetColors.Background)
                    .cornerRadius(16.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity(MainActivity::class.java))
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVIDAD RECIENTE",
                            style = TextStyle(
                                color = FinanzWidgetColors.TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    if (recentTransactions.isEmpty()) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin movimientos recientes",
                                style = TextStyle(
                                    color = FinanzWidgetColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    } else {
                        Column(
                            modifier = GlanceModifier.fillMaxWidth()
                        ) {
                            recentTransactions.forEachIndexed { index, transaction ->
                                if (index > 0) {
                                    Spacer(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(FinanzWidgetColors.Outline)
                                    )
                                }

                                Row(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val category = categoryMap[transaction.categoryId]
                                    val categoryColor = parseHexColor(category?.color)

                                    // Visual color dot/bar representing category
                                    Box(
                                        modifier = GlanceModifier
                                            .size(width = 4.dp, height = 24.dp)
                                            .background(ColorProvider(categoryColor))
                                            .cornerRadius(2.dp)
                                    ) {}

                                    Spacer(modifier = GlanceModifier.width(8.dp))

                                    // Merchant name & formatted date
                                    Column(
                                        modifier = GlanceModifier.defaultWeight(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        val displayName = when {
                                            !transaction.merchant.isNullOrBlank() -> transaction.merchant
                                            category != null -> category.name
                                            else -> "Movimiento"
                                        }
                                        Text(
                                            text = displayName,
                                            style = TextStyle(
                                                color = FinanzWidgetColors.TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = formatTransactionTime(transaction.timestamp),
                                            style = TextStyle(
                                                color = FinanzWidgetColors.TextMuted,
                                                fontSize = 9.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }

                                    // Amount formatted based on type
                                    val isExpense = transaction.type == TransactionType.GASTO ||
                                                    transaction.type == TransactionType.GASTO_TC
                                    val isIncome = transaction.type == TransactionType.INGRESO

                                    val (amountText, amountColor) = when {
                                        isIncome -> {
                                            "+" + formatCOP(transaction.amount) to FinanzWidgetColors.SuccessColor
                                        }
                                        isExpense -> {
                                            "-" + formatCOP(transaction.amount) to FinanzWidgetColors.ErrorColor
                                        }
                                        else -> {
                                            formatCOP(transaction.amount) to FinanzWidgetColors.TextMutedColor
                                        }
                                    }

                                    Text(
                                        text = amountText,
                                        style = TextStyle(
                                            color = ColorProvider(amountColor),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseHexColor(hex: String?): Color {
        if (hex.isNullOrBlank()) return FinanzWidgetColors.TextMutedColor
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            FinanzWidgetColors.TextMutedColor
        }
    }

    private fun formatTransactionTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val now = LocalDateTime.now(ZoneId.systemDefault())

        return when {
            dateTime.toLocalDate().isEqual(now.toLocalDate()) -> {
                val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
                "Hoy, " + dateTime.format(formatter)
            }
            dateTime.toLocalDate().isEqual(now.toLocalDate().minusDays(1)) -> {
                val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
                "Ayer, " + dateTime.format(formatter)
            }
            else -> {
                val formatter = DateTimeFormatter.ofPattern("d MMM, hh:mm a", Locale.forLanguageTag("es-CO"))
                dateTime.format(formatter)
            }
        }
    }
}
