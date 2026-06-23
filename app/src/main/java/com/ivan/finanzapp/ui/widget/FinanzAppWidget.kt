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
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.quickadd.QuickAddActivity
import com.ivan.finanzapp.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun accountDao(): AccountDao
    fun creditCardDao(): CreditCardDao
    fun categoryDao(): CategoryDao
    fun transactionDao(): TransactionDao
    fun loanDao(): LoanDao
    fun deferredPurchaseDao(): DeferredPurchaseDao
}

class FinanzAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        
        // Recuperar DAOs usando Hilt EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val accountDao = entryPoint.accountDao()
        val creditCardDao = entryPoint.creditCardDao()

        val accounts = accountDao.getAllAccountsSnapshot()
        
        // Calcular balance y deudas
        var totalBalance = 0.0
        val normalAccounts = accounts.filter { it.type != AccountType.TARJETA_CREDITO }
        for (acc in normalAccounts) {
            totalBalance += acc.currentBalance
        }
        
        val creditCards = accounts.filter { it.type == AccountType.TARJETA_CREDITO }
        var totalDebt = 0.0
        val cardDebtMap = mutableMapOf<String, Double>()
        for (cardAcc in creditCards) {
            val card = creditCardDao.getByAccountId(cardAcc.id)
            if (card != null) {
                totalDebt += card.currentDebt
                cardDebtMap[cardAcc.id] = card.currentDebt
            }
        }
        
        val netBalance = totalBalance - totalDebt

        // Paleta Dark Slate Premium (compatible con cualquier versión de Glance)
        val widgetBgColor = ColorProvider(Color(0xFF1E1E1E))
        val textPrimaryColor = ColorProvider(Color(0xFFF5F6F8))
        val textSecondaryColor = ColorProvider(Color(0xFF8E95A5))
        val dividerColor = ColorProvider(Color(0xFF2D323E))
        val brandGreen = Color(0xFF2E7D32)
        val errorRedColor = ColorProvider(Color(0xFFE57373))

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(widgetBgColor)
                    .cornerRadius(16.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Cabecera: Título + Botón [+] Registrar
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "F I N A N Z A S",
                            style = TextStyle(
                                color = textSecondaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        
                        // Botón para Registrar Gasto manual
                        Row(
                            modifier = GlanceModifier
                                .background(brandGreen)
                                .cornerRadius(8.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .clickable(actionStartActivity(QuickAddActivity::class.java)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+ Registrar",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Saldo disponible consolidado
                    Text(
                        text = "Disponible Total",
                        style = TextStyle(
                            color = textSecondaryColor,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = formatCOP(netBalance),
                        style = TextStyle(
                            color = textPrimaryColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Línea divisora
                    Spacer(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(dividerColor)
                    )

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Cuentas individuales (Top 3)
                    Column(
                        modifier = GlanceModifier.fillMaxWidth()
                    ) {
                        accounts.take(3).forEach { account ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable(actionStartActivity(MainActivity::class.java)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = account.name,
                                    style = TextStyle(
                                        color = textPrimaryColor,
                                        fontSize = 12.sp
                                    ),
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                
                                val isCredit = account.type == AccountType.TARJETA_CREDITO
                                val debt = if (isCredit) cardDebtMap[account.id] ?: 0.0 else 0.0
                                val balanceText = if (isCredit) "Deuda: ${formatCOP(debt)}" else formatCOP(account.currentBalance)
                                
                                Text(
                                    text = balanceText,
                                    style = TextStyle(
                                        color = if (isCredit) errorRedColor else textSecondaryColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
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
