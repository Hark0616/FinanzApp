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
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.LoanPaymentDao
import com.ivan.finanzapp.data.local.dao.MerchantCategoryMappingDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.data.local.entity.LoanPaymentEntity
import com.ivan.finanzapp.data.local.entity.MerchantCategoryMappingEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.data.local.entity.AssetEntity
import com.ivan.finanzapp.data.local.dao.AssetDao
import com.ivan.finanzapp.data.local.entity.CustomRuleEntity
import com.ivan.finanzapp.data.local.dao.CustomRuleDao
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
        CustomRuleEntity::class
    ],
    version = 10,
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
                INSTANCE ?: build(context, passphrase).also { INSTANCE = it }
            }
        }

        private fun build(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory: SupportSQLiteOpenHelper.Factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
