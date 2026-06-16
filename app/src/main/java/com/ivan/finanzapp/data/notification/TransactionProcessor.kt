package com.ivan.finanzapp.data.notification

import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.data.notification.parsers.ParserDispatcher
import com.ivan.finanzapp.data.remote.TransactionAiClassifier
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.domain.usecase.AccountResolver
import com.ivan.finanzapp.domain.usecase.CategoryResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val LOW_CONFIDENCE_THRESHOLD = 0.65

/**
 * Orquesta el pipeline completo de procesamiento de notificaciones:
 *
 * 1. Filtra notificaciones irrelevantes (paquetes no bancarios).
 * 2. Intenta parsear con [ParserDispatcher] (reglas locales, Nivel 1).
 * 3. Si falla, llama a [TransactionAiClassifier] (LLM, Nivel 3).
 * 4. Resuelve categoría con [CategoryResolver].
 * 5. Resuelve cuenta con [AccountResolver].
 * 6. Deduplica usando hash determinístico.
 * 7. Persiste en Room.
 * 8. Actualiza saldo de la cuenta y deuda de la tarjeta de crédito si aplica.
 */
@Singleton
class TransactionProcessor @Inject constructor(
    private val parserDispatcher: ParserDispatcher,
    private val aiClassifier: TransactionAiClassifier,
    private val categoryResolver: CategoryResolver,
    private val accountResolver: AccountResolver,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao
) {

    // Scope propio para no bloquear el NotificationListenerService
    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Punto de entrada: llamado desde [TransactionNotificationListenerService]
     * por cada notificación que llega de un paquete bancario.
     *
     * Es no-bloqueante: lanza una coroutine en [processorScope].
     */
    fun processAsync(
        packageName: String,
        title: String,
        text: String,
        postedAtMillis: Long = System.currentTimeMillis()
    ) {
        if (!parserDispatcher.isSupportedPackage(packageName)) return

        processorScope.launch {
            process(packageName, title, text, postedAtMillis)
        }
    }

    private suspend fun process(
        packageName: String,
        title: String,
        text: String,
        postedAtMillis: Long
    ) {
        // Paso 1: intentar con parsers por reglas
        var parsed = parserDispatcher.dispatch(packageName, title, text)
        var aiCategoryName: String? = null
        var usedAi = false

        // Paso 2: fallback de IA si las reglas no matchearon
        if (parsed == null) {
            val aiResult = aiClassifier.classifyWithCategory(packageName, title, text)
            if (aiResult != null) {
                parsed = aiResult.first
                aiCategoryName = aiResult.second
                usedAi = true
            }
        }

        // Si ni reglas ni IA pudieron parsear, ignoramos la notificación
        // (probablemente es una notificación promocional, no transaccional)
        val transaction = parsed ?: return

        // Paso 3: resolver categoría (niveles 1, 2 y 3 ya coordinados en CategoryResolver)
        val categoryId = categoryResolver.resolve(transaction.merchant, aiCategoryName)

        // Paso 4: resolver cuenta
        val accountId = accountResolver.resolveAccountId(transaction.source, transaction.type)

        // Paso 5: generar id determinístico para deduplicación
        val id = TransactionIdGenerator.generate(
            packageName = packageName,
            amount = transaction.amount,
            merchant = transaction.merchant,
            timestampMillis = postedAtMillis
        )

        // Paso 6: construir y persistir la entidad
        val entity = TransactionEntity(
            id = id,
            accountId = accountId,
            amount = transaction.amount,
            type = transaction.type,
            merchant = transaction.merchant,
            categoryId = categoryId,
            rawNotification = "[$packageName] $title | $text",
            timestamp = postedAtMillis,
            confirmedByAI = usedAi,
            needsReview = transaction.confidence < LOW_CONFIDENCE_THRESHOLD
        )

        val inserted = transactionDao.insertIfNotExists(entity)

        // Si inserted == 0, era un duplicado; no actualizamos saldos
        if (inserted == 0L) return

        // Paso 7: actualizar saldo de la cuenta
        if (accountId != null) {
            when (transaction.type) {
                TransactionType.INGRESO -> {
                    if (transaction.availableBalance != null) {
                        accountDao.setAbsoluteBalance(accountId, transaction.availableBalance)
                    } else {
                        accountDao.adjustBalance(accountId, +transaction.amount)
                    }
                }
                TransactionType.GASTO, TransactionType.TRANSFERENCIA -> {
                    if (transaction.availableBalance != null) {
                        accountDao.setAbsoluteBalance(accountId, transaction.availableBalance)
                    } else {
                        accountDao.adjustBalance(accountId, -transaction.amount)
                    }
                }
                TransactionType.GASTO_TC -> {
                    // Para tarjeta de crédito: actualizar deuda, no saldo de cuenta de ahorros
                    updateCreditCardDebt(accountId, +transaction.amount)
                }
                TransactionType.PAGO_TC -> {
                    updateCreditCardDebt(accountId, -transaction.amount)
                }
            }
        }
    }

    private suspend fun updateCreditCardDebt(accountId: String, delta: Double) {
        val card = creditCardDao.getByAccountId(accountId) ?: return
        creditCardDao.adjustDebt(card.id, delta)
    }
}
