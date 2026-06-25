package com.ivan.finanzapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ivan.finanzapp.data.local.converters.Converters
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.DebtPaymentApplicationDao
import com.ivan.finanzapp.data.local.dao.FinancialAdjustmentDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.LoanPaymentDao
import com.ivan.finanzapp.data.local.dao.MerchantCategoryMappingDao
import com.ivan.finanzapp.data.local.dao.NotificationSyncLedgerDao
import com.ivan.finanzapp.data.local.dao.PaymentMatchSuggestionDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.DebtPaymentApplicationEntity
import com.ivan.finanzapp.data.local.entity.FinancialAdjustmentEntity
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.data.local.entity.LoanPaymentEntity
import com.ivan.finanzapp.data.local.entity.MerchantCategoryMappingEntity
import com.ivan.finanzapp.data.local.entity.NotificationSyncLedgerEntity
import com.ivan.finanzapp.data.local.entity.PaymentMatchSuggestionEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.data.local.entity.AssetEntity
import com.ivan.finanzapp.data.local.dao.AssetDao
import com.ivan.finanzapp.data.local.entity.CustomRuleEntity
import com.ivan.finanzapp.data.local.dao.CustomRuleDao
import com.ivan.finanzapp.data.local.entity.SyncDeleteLogEntity
import com.ivan.finanzapp.data.local.dao.SyncDeleteLogDao
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        AccountEntity::class,
        CreditCardEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MerchantCategoryMappingEntity::class,
        LoanEntity::class,
        LoanPaymentEntity::class,
        DeferredPurchaseEntity::class,
        AssetEntity::class,
        CustomRuleEntity::class,
        NotificationSyncLedgerEntity::class,
        SyncDeleteLogEntity::class,
        PaymentMatchSuggestionEntity::class,
        DebtPaymentApplicationEntity::class,
        FinancialAdjustmentEntity::class
    ],
    version = 16,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantCategoryMappingDao(): MerchantCategoryMappingDao
    abstract fun loanDao(): LoanDao
    abstract fun loanPaymentDao(): LoanPaymentDao
    abstract fun deferredPurchaseDao(): DeferredPurchaseDao
    abstract fun assetDao(): AssetDao
    abstract fun customRuleDao(): CustomRuleDao
    abstract fun notificationSyncLedgerDao(): NotificationSyncLedgerDao
    abstract fun syncDeleteLogDao(): SyncDeleteLogDao
    abstract fun paymentMatchSuggestionDao(): PaymentMatchSuggestionDao
    abstract fun debtPaymentApplicationDao(): DebtPaymentApplicationDao
    abstract fun financialAdjustmentDao(): FinancialAdjustmentDao


    companion object {
        private const val DB_NAME = "finanzapp.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `loan_payments` (
                        `id` TEXT NOT NULL,
                        `loanId` TEXT NOT NULL,
                        `transactionId` TEXT,
                        `installmentNumber` INTEGER NOT NULL,
                        `scheduledPaymentAmount` REAL NOT NULL,
                        `actualPaymentAmount` REAL NOT NULL,
                        `interestAccruedAmount` REAL NOT NULL,
                        `interestPaidAmount` REAL NOT NULL,
                        `unpaidInterestAmount` REAL NOT NULL,
                        `principalAmount` REAL NOT NULL,
                        `remainingAmountBefore` REAL NOT NULL,
                        `remainingAmountAfter` REAL NOT NULL,
                        `paymentDate` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_loanId` ON `loan_payments` (`loanId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_transactionId` ON `loan_payments` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_paymentDate` ON `loan_payments` (`paymentDate`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_rules` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `regexPattern` TEXT NOT NULL,
                        `transactionType` TEXT NOT NULL,
                        `bankSource` TEXT NOT NULL,
                        `amountFormatType` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN lastFourDigits TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loans ADD COLUMN monthlyInsuranceAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loans ADD COLUMN monthlyFeeAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loan_payments ADD COLUMN scheduledInsuranceAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loan_payments ADD COLUMN insurancePaidAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loan_payments ADD COLUMN unpaidInsuranceAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loan_payments ADD COLUMN scheduledFeeAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loan_payments ADD COLUMN feePaidAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loan_payments ADD COLUMN unpaidFeeAmount REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loans ADD COLUMN interestRateInputValue REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loans ADD COLUMN interestRateType TEXT NOT NULL DEFAULT 'MONTHLY_EFFECTIVE'")
                db.execSQL("ALTER TABLE loans ADD COLUMN amortizationType TEXT NOT NULL DEFAULT 'FIXED_INSTALLMENT'")
                db.execSQL("UPDATE loans SET interestRateInputValue = monthlyInterestRate")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loans ADD COLUMN fixedPrincipalAmount REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loan_payments ADD COLUMN extraPrincipalAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loan_payments ADD COLUMN unappliedPaymentAmount REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notification_sync_ledger` (
                        `id` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `postedAtMillis` INTEGER NOT NULL,
                        `receivedAtMillis` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `statusReason` TEXT,
                        `transactionId` TEXT,
                        `accountId` TEXT,
                        `categoryId` TEXT,
                        `transactionType` TEXT,
                        `amount` REAL,
                        `merchant` TEXT,
                        `bankSource` TEXT,
                        `confidence` REAL,
                        `classifierSource` TEXT,
                        `errorMessage` TEXT,
                        `processedAtMillis` INTEGER,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_sync_ledger_status` ON `notification_sync_ledger` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_sync_ledger_postedAtMillis` ON `notification_sync_ledger` (`postedAtMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_sync_ledger_receivedAtMillis` ON `notification_sync_ledger` (`receivedAtMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_sync_ledger_transactionId` ON `notification_sync_ledger` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_sync_ledger_packageName` ON `notification_sync_ledger` (`packageName`)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_delete_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `tableName` TEXT NOT NULL,
                        `recordId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // 2. Create triggers
                val tables = listOf(
                    "accounts" to "id",
                    "credit_cards" to "id",
                    "categories" to "id",
                    "transactions" to "id",
                    "loans" to "id",
                    "loan_payments" to "id",
                    "deferred_purchases" to "id",
                    "assets" to "id",
                    "custom_rules" to "id",
                    "notification_sync_ledger" to "id"
                )
                for ((table, pk) in tables) {
                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS `log_${table}_delete` AFTER DELETE ON `$table`
                        BEGIN
                            INSERT INTO `sync_delete_log` (`tableName`, `recordId`, `createdAt`)
                            VALUES ('$table', OLD.`$pk`, CAST((strftime('%s','now') * 1000) AS INTEGER));
                        END;
                        """.trimIndent()
                    )
                }
                
                // For merchant_category_mappings (pk is merchantKey)
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `log_merchant_category_mappings_delete` AFTER DELETE ON `merchant_category_mappings`
                    BEGIN
                        INSERT INTO `sync_delete_log` (`tableName`, `recordId`, `createdAt`)
                        VALUES ('merchant_category_mappings', OLD.`merchantKey`, CAST((strftime('%s','now') * 1000) AS INTEGER));
                    END;
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_match_suggestions` (
                        `id` TEXT NOT NULL,
                        `sourceTransactionId` TEXT NOT NULL,
                        `targetType` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `targetName` TEXT NOT NULL,
                        `expectedAmount` REAL NOT NULL,
                        `actualAmount` REAL NOT NULL,
                        `differenceAmount` REAL NOT NULL,
                        `confidence` REAL NOT NULL,
                        `reason` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER,
                        `acceptedApplicationId` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sourceTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_match_suggestions_sourceTransactionId` ON `payment_match_suggestions` (`sourceTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payment_match_suggestions_status` ON `payment_match_suggestions` (`status`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_match_suggestions_sourceTransactionId_targetType_targetId` ON `payment_match_suggestions` (`sourceTransactionId`, `targetType`, `targetId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `debt_payment_applications` (
                        `id` TEXT NOT NULL,
                        `sourceTransactionId` TEXT NOT NULL,
                        `suggestionId` TEXT,
                        `targetType` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `targetName` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `expectedAmount` REAL NOT NULL,
                        `differenceAmount` REAL NOT NULL,
                        `applicationType` TEXT NOT NULL,
                        `appliedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sourceTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_debt_payment_applications_sourceTransactionId` ON `debt_payment_applications` (`sourceTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payment_applications_suggestionId` ON `debt_payment_applications` (`suggestionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payment_applications_targetType_targetId` ON `debt_payment_applications` (`targetType`, `targetId`)")

                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `log_payment_match_suggestions_delete` AFTER DELETE ON `payment_match_suggestions`
                    BEGIN
                        INSERT INTO `sync_delete_log` (`tableName`, `recordId`, `createdAt`)
                        VALUES ('payment_match_suggestions', OLD.`id`, CAST((strftime('%s','now') * 1000) AS INTEGER));
                    END;
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `log_debt_payment_applications_delete` AFTER DELETE ON `debt_payment_applications`
                    BEGIN
                        INSERT INTO `sync_delete_log` (`tableName`, `recordId`, `createdAt`)
                        VALUES ('debt_payment_applications', OLD.`id`, CAST((strftime('%s','now') * 1000) AS INTEGER));
                    END;
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_adjustments` (
                        `id` TEXT NOT NULL,
                        `targetType` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `targetName` TEXT NOT NULL,
                        `previousValue` REAL NOT NULL,
                        `newValue` REAL NOT NULL,
                        `delta` REAL NOT NULL,
                        `reason` TEXT NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_adjustments_targetId` ON `financial_adjustments` (`targetId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_adjustments_targetType` ON `financial_adjustments` (`targetType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_adjustments_createdAt` ON `financial_adjustments` (`createdAt`)")
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `log_financial_adjustments_delete` AFTER DELETE ON `financial_adjustments`
                    BEGIN
                        INSERT INTO `sync_delete_log` (`tableName`, `recordId`, `createdAt`)
                        VALUES ('financial_adjustments', OLD.`id`, CAST((strftime('%s','now') * 1000) AS INTEGER));
                    END;
                    """.trimIndent()
                )
            }
        }

        val DB_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                createTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                createTriggers(db)
            }

            private fun createTriggers(db: SupportSQLiteDatabase) {
                val tables = listOf(
                    "accounts" to "id",
                    "credit_cards" to "id",
                    "categories" to "id",
                    "transactions" to "id",
                    "loans" to "id",
                    "loan_payments" to "id",
                    "deferred_purchases" to "id",
                    "assets" to "id",
                    "custom_rules" to "id",
                    "notification_sync_ledger" to "id",
                    "payment_match_suggestions" to "id",
                    "debt_payment_applications" to "id",
                    "financial_adjustments" to "id"
                )
                for ((table, pk) in tables) {
                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS `log_${table}_delete` AFTER DELETE ON `$table`
                        BEGIN
                            INSERT INTO `sync_delete_log` (`tableName`, `recordId`, `createdAt`)
                            VALUES ('$table', OLD.`$pk`, CAST((strftime('%s','now') * 1000) AS INTEGER));
                        END;
                        """.trimIndent()
                    )
                }
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `log_merchant_category_mappings_delete` AFTER DELETE ON `merchant_category_mappings`
                    BEGIN
                        INSERT INTO `sync_delete_log` (`tableName`, `recordId`, `createdAt`)
                        VALUES ('merchant_category_mappings', OLD.`merchantKey`, CAST((strftime('%s','now') * 1000) AS INTEGER));
                    END;
                    """.trimIndent()
                )
            }
        }

        /**
         * Crea (o retorna) la instancia única de la base de datos, cifrada
         * con SQLCipher usando [passphrase].
         *
         * La passphrase se genera y guarda una sola vez en
         * EncryptedSharedPreferences (ver [com.ivan.finanzapp.data.local.SecurePrefs]),
         * por lo que nunca se almacena en texto plano dentro del código ni
         * en preferencias normales.
         */
        fun getInstance(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, passphrase).also { db ->
                    INSTANCE = db
                    db.registerWidgetUpdateObserver(context.applicationContext)
                }
            }
        }

        private fun AppDatabase.registerWidgetUpdateObserver(context: Context) {
            val observer = object : androidx.room.InvalidationTracker.Observer(
                arrayOf("accounts", "credit_cards", "deferred_purchases", "transactions", "categories", "loans")
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    com.ivan.finanzapp.ui.widget.WidgetUpdater.updateAllWidgets(context)
                }
            }
            this.invalidationTracker.addObserver(observer)
        }

        private fun build(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory: SupportSQLiteOpenHelper.Factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16
                )
                .addCallback(DB_CALLBACK)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
