package com.ivan.finanzapp.data.remote

import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.local.AppDatabase
import com.ivan.finanzapp.data.local.dao.*
import androidx.room.withTransaction
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SyncSummary(
    val remoteDeletes: Int,
    val uploadedRows: Int,
    val downloadedRows: Int
) {
    val totalRowsTouched: Int = remoteDeletes + uploadedRows + downloadedRows
}

@Singleton
class SupabaseSyncManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authManager: SupabaseAuthManager,
    private val securePrefs: SecurePrefs,
    private val database: AppDatabase,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val merchantMappingDao: MerchantCategoryMappingDao,
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val assetDao: AssetDao,
    private val customRuleDao: CustomRuleDao,
    private val notificationSyncLedgerDao: NotificationSyncLedgerDao,
    private val paymentMatchSuggestionDao: PaymentMatchSuggestionDao,
    private val debtPaymentApplicationDao: DebtPaymentApplicationDao,
    private val financialAdjustmentDao: FinancialAdjustmentDao,
    private val syncDeleteLogDao: SyncDeleteLogDao
) {
    private val postgrestModule = supabaseClient.postgrest

    /**
     * Sincroniza bidireccionalmente los datos locales con la nube en Supabase.
     * 1. Elimina en Supabase los registros borrados localmente (según sync_delete_log).
     * 2. Sube (upsert) todos los registros locales de las entidades sincronizables.
     * 3. Descarga (select) y guarda localmente todos los registros del usuario en la nube.
     */
    suspend fun sync(): Result<SyncSummary> = withContext(Dispatchers.IO) {
        try {
            val userId = authManager.currentUser?.id ?: return@withContext Result.failure(
                Exception("Usuario no autenticado. Inicia sesión primero.")
            )
            var remoteDeletes = 0
            var uploadedRows = 0
            var downloadedRows = 0

            // ==========================================
            // FASE 1: PROCESAR ELIMINACIONES
            // ==========================================
            val deleteLogs = syncDeleteLogDao.getAll()
            for (log in deleteLogs) {
                try {
                    postgrestModule.from(log.tableName).delete {
                        filter {
                            if (log.tableName == "merchant_category_mappings") {
                                eq("merchantKey", log.recordId)
                            } else {
                                eq("id", log.recordId)
                            }
                            eq("user_id", userId)
                        }
                    }
                    syncDeleteLogDao.delete(log)
                    remoteDeletes += 1
                } catch (e: Exception) {
                    // Si hay un error de red, guardamos el log para intentar borrar después.
                }
            }

            // ==========================================
            // FASE 2: SUBIR DATOS LOCALES A LA NUBE (UPSERT)
            // ==========================================
            
            // 1. Cuentas (Accounts)
            val localAccounts = accountDao.getAllAccountsSnapshot()
            if (localAccounts.isNotEmpty()) {
                val dtos = localAccounts.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("accounts").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 2. Tarjetas de Crédito (Credit Cards)
            val localCards = creditCardDao.getAllSnapshot()
            if (localCards.isNotEmpty()) {
                val dtos = localCards.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("credit_cards").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 3. Categorías Personalizadas (Categories - exclude default ones)
            val localCategories = categoryDao.getAllSnapshot().filter { !it.isDefault }
            if (localCategories.isNotEmpty()) {
                val dtos = localCategories.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("categories").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 4. Transacciones (Transactions)
            val localTransactions = transactionDao.getAllSnapshot()
            if (localTransactions.isNotEmpty()) {
                val dtos = localTransactions.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("transactions").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 5. Mapeos Comercio-Categoría (Merchant category mappings)
            val localMappings = merchantMappingDao.getAll()
            if (localMappings.isNotEmpty()) {
                val dtos = localMappings.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("merchant_category_mappings").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 6. Préstamos / Créditos (Loans)
            val localLoans = loanDao.getAllLoansSnapshot()
            if (localLoans.isNotEmpty()) {
                val dtos = localLoans.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("loans").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 7. Historial de Pagos de Cuotas (Loan Payments)
            val localPayments = loanPaymentDao.getAllSnapshot()
            if (localPayments.isNotEmpty()) {
                val dtos = localPayments.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("loan_payments").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 8. Compras Diferidas (Deferred Purchases)
            val localPurchases = deferredPurchaseDao.getAllSnapshot()
            if (localPurchases.isNotEmpty()) {
                val dtos = localPurchases.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("deferred_purchases").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 9. Activos Físicos/Otros (Assets)
            val localAssets = assetDao.getAllSnapshot()
            if (localAssets.isNotEmpty()) {
                val dtos = localAssets.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("assets").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 10. Reglas Personalizadas (Custom Rules)
            val localRules = customRuleDao.getAll()
            if (localRules.isNotEmpty()) {
                val dtos = localRules.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("custom_rules").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 11. Registro Histórico de Notificaciones (Sync Ledger)
            val localLedger = notificationSyncLedgerDao.getAllSnapshot()
            if (localLedger.isNotEmpty()) {
                val dtos = localLedger.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("notification_sync_ledger").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 12. Sugerencias de conciliación de pagos
            val localSuggestions = paymentMatchSuggestionDao.getAllSnapshot()
            if (localSuggestions.isNotEmpty()) {
                val dtos = localSuggestions.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("payment_match_suggestions").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 13. Aplicaciones confirmadas de pagos a deuda
            val localApplications = debtPaymentApplicationDao.getAllSnapshot()
            if (localApplications.isNotEmpty()) {
                val dtos = localApplications.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("debt_payment_applications").upsert(dtos)
                uploadedRows += dtos.size
            }

            // 14. Ajustes financieros manuales auditados
            val localAdjustments = financialAdjustmentDao.getAllSnapshot()
            if (localAdjustments.isNotEmpty()) {
                val dtos = localAdjustments.map { it.toDto().copy(user_id = userId) }
                postgrestModule.from("financial_adjustments").upsert(dtos)
                uploadedRows += dtos.size
            }

            // ==========================================
            // FASE 3: DESCARGAR CAMBIOS NUEVOS DESDE LA NUBE
            // ==========================================
            
            // 1. Categorías del usuario
            val remoteCategories = postgrestModule.from("categories")
                .select()
                .decodeList<CategoryDto>()

            // 2. Cuentas
            val remoteAccounts = postgrestModule.from("accounts")
                .select()
                .decodeList<AccountDto>()

            // 3. Tarjetas de Crédito
            val remoteCards = postgrestModule.from("credit_cards")
                .select()
                .decodeList<CreditCardDto>()

            // 4. Transacciones
            val remoteTransactions = postgrestModule.from("transactions")
                .select()
                .decodeList<TransactionDto>()

            // 5. Mapeo Comercio-Categoría
            val remoteMappings = postgrestModule.from("merchant_category_mappings")
                .select()
                .decodeList<MerchantCategoryMappingDto>()

            // 6. Préstamos
            val remoteLoans = postgrestModule.from("loans")
                .select()
                .decodeList<LoanDto>()

            // 7. Pagos de Cuotas
            val remotePayments = postgrestModule.from("loan_payments")
                .select()
                .decodeList<LoanPaymentDto>()

            // 8. Compras Diferidas
            val remotePurchases = postgrestModule.from("deferred_purchases")
                .select()
                .decodeList<DeferredPurchaseDto>()

            // 9. Activos
            val remoteAssets = postgrestModule.from("assets")
                .select()
                .decodeList<AssetDto>()

            // 10. Reglas
            val remoteRules = postgrestModule.from("custom_rules")
                .select()
                .decodeList<CustomRuleDto>()

            // 11. Ledger de Notificaciones
            val remoteLedger = postgrestModule.from("notification_sync_ledger")
                .select()
                .decodeList<NotificationSyncLedgerDto>()

            // 12. Sugerencias de conciliación de pagos
            val remoteSuggestions = postgrestModule.from("payment_match_suggestions")
                .select()
                .decodeList<PaymentMatchSuggestionDto>()

            // 13. Aplicaciones confirmadas de pagos a deuda
            val remoteApplications = postgrestModule.from("debt_payment_applications")
                .select()
                .decodeList<DebtPaymentApplicationDto>()

            // 14. Ajustes financieros manuales auditados
            val remoteAdjustments = postgrestModule.from("financial_adjustments")
                .select()
                .decodeList<FinancialAdjustmentDto>()

            downloadedRows += remoteCategories.size +
                    remoteAccounts.size +
                    remoteCards.size +
                    remoteTransactions.size +
                    remoteMappings.size +
                    remoteLoans.size +
                    remotePayments.size +
                    remotePurchases.size +
                    remoteAssets.size +
                    remoteRules.size +
                    remoteLedger.size +
                    remoteSuggestions.size +
                    remoteApplications.size +
                    remoteAdjustments.size

            database.withTransaction {
                for (catDto in remoteCategories) {
                    categoryDao.upsert(catDto.toEntity())
                }
                for (accDto in remoteAccounts) {
                    accountDao.upsert(accDto.toEntity())
                }
                for (cardDto in remoteCards) {
                    creditCardDao.upsert(cardDto.toEntity())
                }
                transactionDao.upsertAll(remoteTransactions.map { it.toEntity() })
                for (mapDto in remoteMappings) {
                    merchantMappingDao.upsert(mapDto.toEntity())
                }
                for (loanDto in remoteLoans) {
                    loanDao.upsert(loanDto.toEntity())
                }
                loanPaymentDao.upsertAll(remotePayments.map { it.toEntity() })
                for (dpDto in remotePurchases) {
                    deferredPurchaseDao.upsert(dpDto.toEntity())
                }
                for (assetDto in remoteAssets) {
                    assetDao.upsert(assetDto.toEntity())
                }
                for (ruleDto in remoteRules) {
                    customRuleDao.upsert(ruleDto.toEntity())
                }
                notificationSyncLedgerDao.upsertAll(remoteLedger.map { it.toEntity() })
                paymentMatchSuggestionDao.upsertAll(remoteSuggestions.map { it.toEntity() })
                debtPaymentApplicationDao.upsertAll(remoteApplications.map { it.toEntity() })
                financialAdjustmentDao.upsertAll(remoteAdjustments.map { it.toEntity() })
            }

            securePrefs.setLastCloudSyncAt(System.currentTimeMillis())
            Result.success(
                SyncSummary(
                    remoteDeletes = remoteDeletes,
                    uploadedRows = uploadedRows,
                    downloadedRows = downloadedRows
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
