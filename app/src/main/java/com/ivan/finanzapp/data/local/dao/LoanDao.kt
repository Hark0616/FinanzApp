package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ivan.finanzapp.data.local.entity.LoanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {

    @Query("SELECT * FROM loans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoanById(id: String): LoanEntity?

    @Query("SELECT * FROM loans WHERE id = :id")
    fun observeLoanById(id: String): Flow<LoanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(loan: LoanEntity)

    @Query("SELECT * FROM loans")
    suspend fun getAllLoansSnapshot(): List<LoanEntity>

    @Update
    suspend fun update(loan: LoanEntity)

    @Query("UPDATE loans SET remainingAmount = :remainingAmount, paidInstallments = :paidInstallments, nextPaymentDate = :nextPaymentDate WHERE id = :loanId")
    suspend fun updateLoanPaymentProgress(
        loanId: String,
        remainingAmount: Double,
        paidInstallments: Int,
        nextPaymentDate: Long
    )

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun delete(id: String)
}
