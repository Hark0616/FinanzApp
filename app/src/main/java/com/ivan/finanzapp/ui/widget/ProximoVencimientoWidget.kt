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
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.ui.components.formatCOP
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PaymentDueInfo(
    val name: String,
    val dueDate: LocalDate,
    val daysRemaining: Int,
    val amount: Double,
    val isCreditCard: Boolean
)

class ProximoVencimientoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val accountDao = entryPoint.accountDao()
        val creditCardDao = entryPoint.creditCardDao()
        val loanDao = entryPoint.loanDao()
        val deferredPurchaseDao = entryPoint.deferredPurchaseDao()

        val calculator = CreditCardCalculator()

        // 1. Obtener deudas de tarjetas de crédito
        val creditCards = creditCardDao.getAllSnapshot()
        val cardDueList = creditCards.filter { it.currentDebt > 0.0 }.map { card ->
            val account = accountDao.getAccountById(card.accountId)
            val name = account?.name ?: "Tarjeta de Crédito"
            val cardPurchases = deferredPurchaseDao.getByCardIdSnapshot(card.id)
            val nextDue = calculator.nextPaymentDueDate(card)
            val days = calculator.daysUntilPaymentDue(card)
            val minPayment = calculator.minimumPayment(card, cardPurchases)
            PaymentDueInfo(
                name = name,
                dueDate = nextDue,
                daysRemaining = days,
                amount = minPayment,
                isCreditCard = true
            )
        }

        // 2. Obtener cuotas de préstamos
        val loans = loanDao.getAllLoansSnapshot()
        val todayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val loanDueList = loans.filter { loan -> loan.remainingAmount > 0.0 }.map { loan ->
            val days = ((loan.nextPaymentDate - todayMillis) / (24 * 60 * 60 * 1000)).toInt()
            val nextDue = Instant.ofEpochMilli(loan.nextPaymentDate).atZone(ZoneId.systemDefault()).toLocalDate()
            PaymentDueInfo(
                name = loan.name,
                dueDate = nextDue,
                daysRemaining = days,
                amount = loan.monthlyInstallmentAmount,
                isCreditCard = false
            )
        }

        // 3. Consolidar y ordenar por vencimiento más cercano
        val allDues = (cardDueList + loanDueList).sortedBy { it.daysRemaining }
        val nextDue = allDues.firstOrNull()

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
                        text = "PRÓXIMO PAGO",
                        style = TextStyle(
                            color = FinanzWidgetColors.TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    
                    if (nextDue != null) {
                        val statusColor = when {
                            nextDue.daysRemaining <= 3 -> FinanzWidgetColors.ErrorColor
                            nextDue.daysRemaining <= 7 -> FinanzWidgetColors.WarningColor
                            else -> FinanzWidgetColors.SuccessColor
                        }

                        val dayText = when (nextDue.daysRemaining) {
                            0 -> "Vence hoy"
                            1 -> "Vence mañana"
                            else -> "Vence en ${nextDue.daysRemaining} días"
                        }

                        val prefix = if (nextDue.isCreditCard) "Tarjeta" else "Crédito"

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = GlanceModifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$prefix - ${nextDue.name}",
                                style = TextStyle(
                                    color = FinanzWidgetColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = GlanceModifier.defaultWeight(),
                                maxLines = 1
                            )
                            Text(
                                text = formatCOP(nextDue.amount),
                                style = TextStyle(
                                    color = FinanzWidgetColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = dayText,
                            style = TextStyle(
                                color = ColorProvider(statusColor),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = GlanceModifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Todo al día",
                                style = TextStyle(
                                    color = FinanzWidgetColors.Success,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "No hay pagos pendientes próximamente",
                            style = TextStyle(
                                color = FinanzWidgetColors.TextSecondary,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
