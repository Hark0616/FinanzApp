package com.ivan.finanzapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ivan.finanzapp.data.local.converters.Converters
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.MerchantCategoryMappingDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.data.local.entity.MerchantCategoryMappingEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.data.local.entity.AssetEntity
import com.ivan.finanzapp.data.local.dao.AssetDao
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        AccountEntity::class,
        CreditCardEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MerchantCategoryMappingEntity::class,
        LoanEntity::class,
        DeferredPurchaseEntity::class,
        AssetEntity::class
    ],
    version = 5,
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
    abstract fun deferredPurchaseDao(): DeferredPurchaseDao
    abstract fun assetDao(): AssetDao


    companion object {
        private const val DB_NAME = "finanzapp.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
