package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivan.finanzapp.data.local.entity.LoanPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanPaymentDao {

    @Query("SELECT * FROM loan_payments ORDER BY paymentDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<LoanPaymentEntity>>

    @Query("SELECT COALESCE(SUM(unpaidInterestAmount), 0.0) FROM loan_payments")
    fun observeTotalUnpaidInterest(): Flow<Double>

    @Query("SELECT COALESCE(SUM(unpaidInsuranceAmount + unpaidFeeAmount), 0.0) FROM loan_payments")
    fun observeTotalUnpaidCharges(): Flow<Double>

    @Query(
        """
        SELECT * FROM loan_payments AS payment
        WHERE payment.id IN (
            SELECT latest.id FROM loan_payments AS latest
            WHERE latest.loanId = payment.loanId
            ORDER BY latest.paymentDate DESC, latest.createdAt DESC
            LIMIT 1
        )
        ORDER BY payment.paymentDate DESC, payment.createdAt DESC
        """
    )
    fun observeLatestByLoan(): Flow<List<LoanPaymentEntity>>

    @Query(
        """
        SELECT * FROM loan_payments
        WHERE loanId = :loanId
        ORDER BY paymentDate DESC, createdAt DESC
        """
    )
    fun observeByLoanId(loanId: String): Flow<List<LoanPaymentEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(unpaidInterestAmount), 0.0)
        FROM loan_payments
        WHERE loanId = :loanId
        """
    )
    suspend fun getUnpaidInterestTotal(loanId: String): Double

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: LoanPaymentEntity): Long
}
