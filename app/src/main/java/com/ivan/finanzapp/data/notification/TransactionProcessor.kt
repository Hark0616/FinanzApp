package com.ivan.finanzapp.data.notification

import androidx.room.withTransaction
import com.ivan.finanzapp.data.local.AppDatabase
import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.CustomRuleDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.dao.NotificationSyncLedgerDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.NotificationProcessingStatus
import com.ivan.finanzapp.data.local.entity.NotificationSyncLedgerEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.data.notification.parsers.ParsedTransaction
import com.ivan.finanzapp.data.notification.parsers.ParserDispatcher
import com.ivan.finanzapp.data.notification.parsers.ParserUtils
import com.ivan.finanzapp.data.remote.LocalAiClassifier
import com.ivan.finanzapp.data.remote.CloudSyncScheduler
import com.ivan.finanzapp.data.remote.TransactionAiClassifier
import com.ivan.finanzapp.data.security.SecureLog
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.domain.usecase.AccountResolver
import com.ivan.finanzapp.domain.usecase.CategoryResolver
import com.ivan.finanzapp.domain.usecase.PaymentReconciliationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.ivan.finanzapp.ui.widget.FinanzAppWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val LOW_CONFIDENCE_THRESHOLD = 0.65
private const val SOURCE_RULES = "RULES"
private const val SOURCE_CUSTOM_RULE = "CUSTOM_RULE"
private const val SOURCE_LOCAL_AI = "LOCAL_AI"
private const val SOURCE_CLOUD_AI = "CLOUD_AI"

/**
 * Orquesta el pipeline completo de procesamiento de notificaciones:
 *
 * 1. Registra la notificacion bancaria cruda en el ledger con estado RECEIVED.
 * 2. Filtra notificaciones no procesables y las marca como IGNORED.
 * 3. Intenta parsear con [ParserDispatcher] (reglas locales, Nivel 1).
 * 4. Intenta reglas personalizadas del usuario.
 * 5. Si falla, usa IA local o nube segun configuracion.
 * 6. Persiste transaccion, saldos, tarjeta y estado final del ledger dentro
 *    de una sola transaccion Room.
 */
@Singleton
class TransactionProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val parserDispatcher: ParserDispatcher,
    private val aiClassifier: TransactionAiClassifier,
    private val localAiClassifier: LocalAiClassifier,
    private val securePrefs: SecurePrefs,
    private val customRuleDao: CustomRuleDao,
    private val categoryResolver: CategoryResolver,
    private val accountResolver: AccountResolver,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val notificationSyncLedgerDao: NotificationSyncLedgerDao,
    private val calculator: CreditCardCalculator,
    private val paymentReconciliationUseCase: PaymentReconciliationUseCase,
    private val cloudSyncScheduler: CloudSyncScheduler
) {

    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Punto de entrada no bloqueante llamado desde [TransactionNotificationListenerService].
     * La primera escritura es siempre el ledger crudo, antes de parsear.
     */
    fun processAsync(
        packageName: String,
        title: String,
        text: String,
        postedAtMillis: Long = System.currentTimeMillis()
    ) {
        processorScope.launch {
            if (!parserDispatcher.isSupportedPackage(packageName)) {
                return@launch
            }

            val receivedAtMillis = System.currentTimeMillis()
            val ledgerEntry = NotificationSyncLedgerEntity(
                id = UUID.randomUUID().toString(),
                packageName = packageName,
                title = title,
                text = text,
                postedAtMillis = postedAtMillis,
                receivedAtMillis = receivedAtMillis,
                updatedAtMillis = receivedAtMillis
            )

            var ledgerPersisted = false
            var shouldScheduleSync = false
            try {
                notificationSyncLedgerDao.insert(ledgerEntry)
                ledgerPersisted = true
                shouldScheduleSync = true
                process(ledgerEntry)
                
                try {
                    FinanzAppWidget().updateAll(context)
                } catch (widgetError: Throwable) {
                    SecureLog.w("TransactionProcessor", "Widget update failed after notification processing.", widgetError)
                }
            } catch (error: Throwable) {
                if (ledgerPersisted) {
                    notificationSyncLedgerDao.update(
                        ledgerEntry.withStatus(
                            status = NotificationProcessingStatus.FAILED,
                            reason = "processing_exception",
                            errorMessage = error.toLedgerError()
                        )
                    )
                }
            } finally {
                if (shouldScheduleSync) {
                    cloudSyncScheduler.syncSoon()
                }
            }
        }
    }

    private suspend fun process(ledgerEntry: NotificationSyncLedgerEntity) {
        SecureLog.d(
            "TransactionProcessor",
            "Processing notification: package=${ledgerEntry.packageName}, textLength=${ledgerEntry.text.length}"
        )

        if (ledgerEntry.title.isBlank() && ledgerEntry.text.isBlank()) {
            SecureLog.d("TransactionProcessor", "Processing ignored: empty_notification")
            notificationSyncLedgerDao.update(
                ledgerEntry.withStatus(
                    status = NotificationProcessingStatus.IGNORED,
                    reason = "empty_notification"
                )
            )
            return
        }

        val parsedResult = parseNotification(ledgerEntry)
        if (parsedResult == null) {
            SecureLog.d("TransactionProcessor", "Processing ignored: no_transaction_detected")
            notificationSyncLedgerDao.update(
                ledgerEntry.withStatus(
                    status = NotificationProcessingStatus.IGNORED,
                    reason = "no_transaction_detected"
                )
            )
            return
        }

        val transaction = parsedResult.transaction
        SecureLog.d(
            "TransactionProcessor",
            "Parsed successfully: type=${transaction.type}, source=${transaction.source}, classifierSource=${parsedResult.classifierSource}"
        )
        
        val fullMessage = "${ledgerEntry.title} ${ledgerEntry.text}"

        var createdTransaction: TransactionEntity? = null
        database.withTransaction {
            val categoryId = categoryResolver.resolve(
                merchant = transaction.merchant,
                suggestedFromAi = parsedResult.aiCategoryName
            )
            val accountId = accountResolver.resolveAccountId(
                source = transaction.source,
                type = transaction.type,
                rawNotificationText = fullMessage
            )
            val transactionId = TransactionIdGenerator.generate(
                packageName = ledgerEntry.packageName,
                amount = transaction.amount,
                merchant = transaction.merchant,
                timestampMillis = ledgerEntry.postedAtMillis
            )

            val entity = TransactionEntity(
                id = transactionId,
                accountId = accountId,
                amount = transaction.amount,
                type = transaction.type,
                merchant = transaction.merchant,
                categoryId = categoryId,
                rawNotification = "[${ledgerEntry.packageName}] ${ledgerEntry.title} | ${ledgerEntry.text}",
                timestamp = ledgerEntry.postedAtMillis,
                confirmedByAI = parsedResult.classifierSource == SOURCE_LOCAL_AI ||
                        parsedResult.classifierSource == SOURCE_CLOUD_AI,
                needsReview = transaction.confidence < LOW_CONFIDENCE_THRESHOLD
            )

            val inserted = transactionDao.insertIfNotExists(entity)
            val parsedLedgerEntry = ledgerEntry.withParsedTransaction(
                status = if (inserted == 0L) {
                    NotificationProcessingStatus.DUPLICATE
                } else {
                    NotificationProcessingStatus.PARSED
                },
                reason = if (inserted == 0L) "duplicate_transaction_id" else null,
                transactionId = transactionId,
                accountId = accountId,
                categoryId = categoryId,
                transaction = transaction,
                classifierSource = parsedResult.classifierSource
            )

            if (inserted == 0L) {
                notificationSyncLedgerDao.update(parsedLedgerEntry)
                return@withTransaction
            }

            applyFinancialSideEffects(
                transaction = transaction,
                transactionId = transactionId,
                accountId = accountId,
                postedAtMillis = ledgerEntry.postedAtMillis
            )
            notificationSyncLedgerDao.update(parsedLedgerEntry)
            createdTransaction = entity
        }
        createdTransaction?.let { paymentReconciliationUseCase.generateSuggestionsForTransaction(it) }
    }

    private suspend fun parseNotification(
        ledgerEntry: NotificationSyncLedgerEntity
    ): ParsedNotificationResult? {
        parserDispatcher.dispatch(ledgerEntry.packageName, ledgerEntry.title, ledgerEntry.text)
            ?.let { parsed ->
                return ParsedNotificationResult(
                    transaction = parsed,
                    aiCategoryName = null,
                    classifierSource = SOURCE_RULES
                )
            }

        parseWithCustomRules(ledgerEntry.title, ledgerEntry.text)?.let { parsed ->
            return ParsedNotificationResult(
                transaction = parsed,
                aiCategoryName = null,
                classifierSource = SOURCE_CUSTOM_RULE
            )
        }

        val mode = securePrefs.getNotificationProcessingMode()
        if (mode == SecurePrefs.MODE_LOCAL_AI || mode == SecurePrefs.MODE_PARSER) {
            val localResult = localAiClassifier.classifyLocally(
                ledgerEntry.packageName,
                ledgerEntry.title,
                ledgerEntry.text
            )
            if (localResult != null) {
                return ParsedNotificationResult(
                    transaction = localResult.first,
                    aiCategoryName = localResult.second,
                    classifierSource = SOURCE_LOCAL_AI
                )
            }

            if (mode == SecurePrefs.MODE_PARSER) {
                val cloudResult = aiClassifier.classifyWithCategory(
                    ledgerEntry.packageName,
                    ledgerEntry.title,
                    ledgerEntry.text
                )
                if (cloudResult != null) {
                    return ParsedNotificationResult(
                        transaction = cloudResult.first,
                        aiCategoryName = cloudResult.second,
                        classifierSource = SOURCE_CLOUD_AI
                    )
                }
            }
        } else if (mode == SecurePrefs.MODE_CLOUD_AI) {
            val cloudResult = aiClassifier.classifyWithCategory(
                ledgerEntry.packageName,
                ledgerEntry.title,
                ledgerEntry.text
            )
            if (cloudResult != null) {
                return ParsedNotificationResult(
                    transaction = cloudResult.first,
                    aiCategoryName = cloudResult.second,
                    classifierSource = SOURCE_CLOUD_AI
                )
            }
        }

        return null
    }

    private suspend fun parseWithCustomRules(title: String, text: String): ParsedTransaction? {
        val customRules = customRuleDao.getAll()
        val fullMessage = "$title $text"
        SecureLog.d("TransactionProcessor", "Evaluating custom rules. Count: ${customRules.size}")

        for (rule in customRules) {
            SecureLog.d("TransactionProcessor", "Evaluating rule '${rule.name}'")
            val regex = compileCustomRuleRegex(rule.regexPattern)
            if (regex == null) {
                SecureLog.d("TransactionProcessor", "Rule '${rule.name}' has invalid regex pattern")
                continue
            }
            val matchResult = regex.find(fullMessage)
            if (matchResult == null) {
                SecureLog.d("TransactionProcessor", "Rule '${rule.name}' pattern did not match message")
                continue
            }
            val amountGroup = matchResult.groups["amount"]
            if (amountGroup == null) {
                SecureLog.d("TransactionProcessor", "Rule '${rule.name}' matched, but group 'amount' was not captured")
                continue
            }
            SecureLog.d("TransactionProcessor", "Rule '${rule.name}' matched")

            val amount = when (rule.amountFormatType) {
                0 -> parseLatAmAmount(amountGroup.value)
                1 -> parseUSAmount(amountGroup.value)
                2 -> parsePlainAmount(amountGroup.value)
                else -> ParserUtils.parseAmount(amountGroup.value)
            }
            if (amount == null) {
                SecureLog.d("TransactionProcessor", "Rule '${rule.name}' failed to parse amount '${amountGroup.value}' using format type ${rule.amountFormatType}")
                continue
            }

            val txType = runCatching { TransactionType.valueOf(rule.transactionType) }
                .getOrDefault(TransactionType.GASTO)
            val bankSource = runCatching {
                com.ivan.finanzapp.domain.model.BankSource.valueOf(rule.bankSource)
            }.getOrDefault(com.ivan.finanzapp.domain.model.BankSource.DESCONOCIDO)

            return ParsedTransaction(
                type = txType,
                amount = amount,
                merchant = matchResult.groups["merchant"]?.value ?: "Transaccion Personalizada",
                availableBalance = null,
                source = bankSource,
                confidence = 1.0
            )
        }

        return null
    }

    private fun compileCustomRuleRegex(pattern: String): Regex? =
        runCatching {
            Regex(deduplicateNamedCaptureGroups(pattern), RegexOption.IGNORE_CASE)
        }.getOrNull()

    private fun deduplicateNamedCaptureGroups(pattern: String): String {
        var normalized = pattern
        for (groupName in listOf("amount", "merchant")) {
            var seen = false
            normalized = Regex("""\(\?<$groupName>""").replace(normalized) {
                if (seen) {
                    "(?:"
                } else {
                    seen = true
                    it.value
                }
            }
        }
        return normalized
    }

    private suspend fun applyFinancialSideEffects(
        transaction: ParsedTransaction,
        transactionId: String,
        accountId: String?,
        postedAtMillis: Long
    ) {
        if (accountId == null) return

        when (transaction.type) {
            TransactionType.INGRESO -> {
                if (transaction.availableBalance != null) {
                    accountDao.setAbsoluteBalance(accountId, transaction.availableBalance)
                } else {
                    accountDao.adjustBalance(accountId, +transaction.amount)
                }
            }

            TransactionType.GASTO,
            TransactionType.TRANSFERENCIA -> {
                if (transaction.availableBalance != null) {
                    accountDao.setAbsoluteBalance(accountId, transaction.availableBalance)
                } else {
                    accountDao.adjustBalance(accountId, -transaction.amount)
                }
            }

            TransactionType.GASTO_TC -> {
                registerDeferredPurchaseFromNotification(
                    accountId = accountId,
                    transactionId = transactionId,
                    merchant = transaction.merchant ?: "Comercio desconocido",
                    amount = transaction.amount,
                    purchaseDate = postedAtMillis
                )
            }

            TransactionType.PAGO_TC -> {
                registerPaymentFromNotification(accountId, transaction.amount)
            }
        }
    }

    private suspend fun registerDeferredPurchaseFromNotification(
        accountId: String,
        transactionId: String,
        merchant: String,
        amount: Double,
        purchaseDate: Long
    ) {
        val card = creditCardDao.getByAccountId(accountId) ?: return
        val purchase = DeferredPurchaseEntity(
            id = transactionId,
            creditCardId = card.id,
            description = merchant,
            totalAmount = amount,
            totalInstallments = 1,
            paidInstallments = 0,
            purchaseDate = purchaseDate
        )
        deferredPurchaseDao.upsert(purchase)
        recalculateCardDebt(card.id)
    }

    private suspend fun registerPaymentFromNotification(accountId: String, amount: Double) {
        val card = creditCardDao.getByAccountId(accountId) ?: return
        val purchases = deferredPurchaseDao.getByCardIdSnapshot(card.id)
        val result = calculator.distributePayment(amount, purchases)

        for (updated in result.updatedPurchases) {
            deferredPurchaseDao.upsert(updated)
        }
        for (deletedId in result.deletedPurchaseIds) {
            deferredPurchaseDao.delete(deletedId)
        }

        recalculateCardDebt(card.id)
    }

    private suspend fun recalculateCardDebt(cardId: String) {
        val purchases = deferredPurchaseDao.getByCardIdSnapshot(cardId)
        val card = creditCardDao.getById(cardId) ?: return
        val newDebt = calculator.totalDeferredDebt(purchases)
        creditCardDao.update(card.copy(currentDebt = newDebt))
    }

    private fun parseLatAmAmount(raw: String): Double? {
        val clean = raw.replace(Regex("""[^\d.,]"""), "")
        val dotIndex = clean.lastIndexOf('.')
        val commaIndex = clean.lastIndexOf(',')
        return if (commaIndex > dotIndex) {
            clean.replace(".", "").replace(",", ".").toDoubleOrNull()
        } else if (commaIndex == -1 && dotIndex != -1) {
            clean.replace(".", "").toDoubleOrNull()
        } else {
            clean.replace(",", ".").toDoubleOrNull()
        }
    }

    private fun parseUSAmount(raw: String): Double? {
        val clean = raw.replace(Regex("""[^\d.,]"""), "")
        val dotIndex = clean.lastIndexOf('.')
        val commaIndex = clean.lastIndexOf(',')
        return if (dotIndex > commaIndex) {
            clean.replace(",", "").toDoubleOrNull()
        } else if (dotIndex == -1 && commaIndex != -1) {
            clean.replace(",", "").toDoubleOrNull()
        } else {
            clean.toDoubleOrNull()
        }
    }

    private fun parsePlainAmount(raw: String): Double? {
        val clean = raw.replace(Regex("""[^\d]"""), "")
        return clean.toDoubleOrNull()
    }

    private fun NotificationSyncLedgerEntity.withStatus(
        status: NotificationProcessingStatus,
        reason: String?,
        errorMessage: String? = null
    ): NotificationSyncLedgerEntity {
        val now = System.currentTimeMillis()
        return copy(
            status = status,
            statusReason = reason,
            errorMessage = errorMessage,
            processedAtMillis = now,
            updatedAtMillis = now
        )
    }

    private fun NotificationSyncLedgerEntity.withParsedTransaction(
        status: NotificationProcessingStatus,
        reason: String?,
        transactionId: String,
        accountId: String?,
        categoryId: String?,
        transaction: ParsedTransaction,
        classifierSource: String
    ): NotificationSyncLedgerEntity {
        val now = System.currentTimeMillis()
        return copy(
            status = status,
            statusReason = reason,
            transactionId = transactionId,
            accountId = accountId,
            categoryId = categoryId,
            transactionType = transaction.type.name,
            amount = transaction.amount,
            merchant = transaction.merchant,
            bankSource = transaction.source.name,
            confidence = transaction.confidence,
            classifierSource = classifierSource,
            processedAtMillis = now,
            updatedAtMillis = now
        )
    }

    private fun Throwable.toLedgerError(): String {
        val message = "${this::class.java.simpleName}: ${message.orEmpty()}"
        return message.take(500)
    }
}

private data class ParsedNotificationResult(
    val transaction: ParsedTransaction,
    val aiCategoryName: String?,
    val classifierSource: String
)
