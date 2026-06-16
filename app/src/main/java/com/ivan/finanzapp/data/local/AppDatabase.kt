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
import com.ivan.finanzapp.data.local.dao.MerchantCategoryMappingDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.MerchantCategoryMappingEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        AccountEntity::class,
        CreditCardEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        MerchantCategoryMappingEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantCategoryMappingDao(): MerchantCategoryMappingDao

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
            val factory: SupportSQLiteOpenHelper.Factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
