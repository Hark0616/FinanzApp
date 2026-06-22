package com.ivan.finanzapp.di

import android.content.Context
import com.ivan.finanzapp.data.local.AppDatabase
import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.AssetDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.LoanPaymentDao
import com.ivan.finanzapp.data.local.dao.MerchantCategoryMappingDao
import com.ivan.finanzapp.data.local.dao.NotificationSyncLedgerDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.ivan.finanzapp.data.local.dao.SyncDeleteLogDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideSecurePrefs(@ApplicationContext context: Context): SecurePrefs =
        SecurePrefs(context)

    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        securePrefs: SecurePrefs
    ): AppDatabase {
        val passphrase = securePrefs.getOrCreateDbPassphrase()
        return AppDatabase.getInstance(context, passphrase)
    }

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCreditCardDao(db: AppDatabase): CreditCardDao = db.creditCardDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideMerchantMappingDao(db: AppDatabase): MerchantCategoryMappingDao =
        db.merchantCategoryMappingDao()

    @Provides
    fun provideLoanDao(db: AppDatabase): LoanDao = db.loanDao()

    @Provides
    fun provideLoanPaymentDao(db: AppDatabase): LoanPaymentDao = db.loanPaymentDao()

    @Provides
    fun provideDeferredPurchaseDao(db: AppDatabase): DeferredPurchaseDao = db.deferredPurchaseDao()

    @Provides
    fun provideAssetDao(db: AppDatabase): AssetDao = db.assetDao()

    @Provides
    fun provideCustomRuleDao(db: AppDatabase): com.ivan.finanzapp.data.local.dao.CustomRuleDao = db.customRuleDao()

    @Provides
    fun provideNotificationSyncLedgerDao(db: AppDatabase): NotificationSyncLedgerDao =
        db.notificationSyncLedgerDao()

    @Provides
    fun provideSyncDeleteLogDao(db: AppDatabase): SyncDeleteLogDao =
        db.syncDeleteLogDao()
}
