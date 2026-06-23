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
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.components.formatCOP
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.ZoneId

class DisponibleNetoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val accountDao = entryPoint.accountDao()
        val creditCardDao = entryPoint.creditCardDao()
        val transactionDao = entryPoint.transactionDao()

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

        val netBalance = totalBalance - totalDebt

        // 3. Determinar el color del indicador basándose en el flujo de caja del mes
        val today = LocalDate.now()
        val monthStart = LocalDate.of(today.year, today.month, 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val monthEnd = LocalDate.now().plusMonths(1).withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val transactions = transactionDao.getByDateRangeSnapshot(monthStart, monthEnd)
        val income = transactions.filter {
            it.type == TransactionType.INGRESO
        }.sumOf { it.amount }
        
        val spent = transactions.filter {
            it.type == TransactionType.GASTO || it.type == TransactionType.GASTO_TC
        }.sumOf { it.amount }
        
        val flow = income - spent
        val dotColor = if (flow >= 0) Color(0xFF81C784) else Color(0xFFFFD54F) // Verde si el flujo es positivo, Amarillo si es negativo

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
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DISPONIBLE NETO",
                            style = TextStyle(
                                color = textSecondaryColor,
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
                        text = formatCOP(netBalance),
                        style = TextStyle(
                            color = textPrimaryColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
