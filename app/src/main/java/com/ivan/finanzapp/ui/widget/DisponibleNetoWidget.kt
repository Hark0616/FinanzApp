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
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.ui.components.formatCOP
import dagger.hilt.android.EntryPointAccessors

class DisponibleNetoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val accountDao = entryPoint.accountDao()
        val creditCardDao = entryPoint.creditCardDao()

        val accounts = accountDao.getAllAccountsSnapshot()

        // 1. Calcular balance total (sin TC)
        val normalAccounts = accounts.filter { it.type != AccountType.TARJETA_CREDITO }
        val totalBalance = normalAccounts.sumOf { it.currentBalance }

        // 2. Calcular deuda total de tarjetas de crédito
        val creditCards = accounts.filter { it.type == AccountType.TARJETA_CREDITO }
        var totalDebt = 0.0
        for (cardAcc in creditCards) {
            val card = creditCardDao.getByAccountId(cardAcc.id)
            if (card != null) {
                totalDebt += card.currentDebt
            }
        }

        val dotColor = when {
            totalBalance <= 0.0 -> FinanzWidgetColors.ErrorColor
            totalDebt > totalBalance -> FinanzWidgetColors.WarningColor
            totalDebt > 0.0 -> FinanzWidgetColors.PrimaryColor
            else -> FinanzWidgetColors.SuccessColor
        }
        val detailText = when {
            totalDebt > 0.0 -> "Tarjetas ${formatCOP(totalDebt)}"
            normalAccounts.isEmpty() -> "Sin cuentas"
            else -> "${normalAccounts.size} cuentas"
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
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DISPONIBLE AHORA",
                            style = TextStyle(
                                color = FinanzWidgetColors.TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        // Pequeño indicador circular de flujo
                        Box(
                            modifier = GlanceModifier
                                .size(8.dp)
                                .background(ColorProvider(dotColor))
                                .cornerRadius(4.dp)
                        ) {}
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = formatCOP(totalBalance),
                        style = TextStyle(
                            color = FinanzWidgetColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = detailText,
                        style = TextStyle(
                            color = if (totalDebt > 0.0) FinanzWidgetColors.Error else FinanzWidgetColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
