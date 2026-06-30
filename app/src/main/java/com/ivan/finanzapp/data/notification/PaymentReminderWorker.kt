package com.ivan.finanzapp.data.notification

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivan.finanzapp.MainActivity
import com.ivan.finanzapp.R
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.ui.components.formatCOP
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@HiltWorker
class PaymentReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val loanDao: LoanDao,
    private val calculator: CreditCardCalculator
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!canPostNotifications()) return Result.success()

        ensureNotificationChannel()

        val today = LocalDate.now()
        val accountsById = accountDao.getAllAccountsSnapshot().associateBy { it.id }
        val notifier = NotificationManagerCompat.from(context)

        creditCardDao.getAllSnapshot()
            .filter { it.currentDebt > 0.0 }
            .forEach { card ->
                val dueDate = nextDateOnOrAfter(today, card.paymentDueDay)
                val daysUntil = ChronoUnit.DAYS.between(today, dueDate).toInt()
                if (daysUntil !in REMINDER_DAYS) return@forEach

                val accountName = accountsById[card.accountId]?.name ?: "Tarjeta"
                val purchases = deferredPurchaseDao.getByCardIdSnapshot(card.id)
                val amount = maxOf(
                    calculator.minimumPayment(card, purchases),
                    calculator.totalMonthlyInstallments(purchases, card.interestRateEA)
                )
                val title = if (daysUntil == 0) {
                    "Pago de tarjeta vence hoy"
                } else {
                    "Pago de tarjeta mañana"
                }
                val body = "$accountName: ${formatCOP(amount)}"
                postReminder(
                    notifier = notifier,
                    idKey = "card:${card.id}:$dueDate:$daysUntil",
                    title = title,
                    body = body
                )
            }

        loanDao.getAllLoansSnapshot()
            .filter { it.remainingAmount > 0.0 }
            .forEach { loan ->
                val dueDate = Instant.ofEpochMilli(loan.nextPaymentDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                val daysUntil = ChronoUnit.DAYS.between(today, dueDate).toInt()
                if (daysUntil !in REMINDER_DAYS) return@forEach

                val title = if (daysUntil == 0) {
                    "Crédito vence hoy"
                } else {
                    "Crédito vence mañana"
                }
                val body = "${loan.name}: ${formatCOP(loan.monthlyInstallmentAmount)}"
                postReminder(
                    notifier = notifier,
                    idKey = "loan:${loan.id}:$dueDate:$daysUntil",
                    title = title,
                    body = body
                )
            }

        return Result.success()
    }

    private fun canPostNotifications(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!notificationsEnabled) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de pagos",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos de pagos próximos de tarjetas y créditos"
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun postReminder(
        notifier: NotificationManagerCompat,
        idKey: String,
        title: String,
        body: String
    ) {
        val contentIntent = PendingIntent.getActivity(
            context,
            idKey.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_add)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notifier.notify(idKey.hashCode(), notification)
    }

    private fun nextDateOnOrAfter(today: LocalDate, dayOfMonth: Int): LocalDate {
        val safeDay = dayOfMonth.coerceAtLeast(1)
        val currentMonth = YearMonth.from(today)
        var date = currentMonth.atDay(safeDay.coerceAtMost(currentMonth.lengthOfMonth()))
        if (date.isBefore(today)) {
            val nextMonth = currentMonth.plusMonths(1)
            date = nextMonth.atDay(safeDay.coerceAtMost(nextMonth.lengthOfMonth()))
        }
        return date
    }

    private companion object {
        const val CHANNEL_ID = "payment_reminders"
        val REMINDER_DAYS = setOf(0, 1)
    }
}
