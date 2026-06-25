package com.ivan.finanzapp.data.remote

import com.ivan.finanzapp.data.local.entity.*
import com.ivan.finanzapp.domain.model.*
import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val type: String,
    val currentBalance: Double,
    val isManualBalance: Boolean,
    val lastFourDigits: String? = null,
    val createdAt: Long,
    val user_id: String? = null
)

fun AccountEntity.toDto(): AccountDto = AccountDto(
    id = id,
    name = name,
    type = type.name,
    currentBalance = currentBalance,
    isManualBalance = isManualBalance,
    lastFourDigits = lastFourDigits,
    createdAt = createdAt
)

fun AccountDto.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = AccountType.entries.firstOrNull { it.name == type } ?: AccountType.OTRO,
    currentBalance = currentBalance,
    isManualBalance = isManualBalance,
    lastFourDigits = lastFourDigits,
    createdAt = createdAt
)

@Serializable
data class CreditCardDto(
    val id: String,
    val accountId: String,
    val creditLimit: Double,
    val currentDebt: Double,
    val cutoffDay: Int,
    val paymentDueDay: Int,
    val minPaymentPercentage: Double = 5.0,
    val minPaymentFloor: Double = 0.0,
    val interestRateEA: Double? = null,
    val user_id: String? = null
)

fun CreditCardEntity.toDto(): CreditCardDto = CreditCardDto(
    id = id,
    accountId = accountId,
    creditLimit = creditLimit,
    currentDebt = currentDebt,
    cutoffDay = cutoffDay,
    paymentDueDay = paymentDueDay,
    minPaymentPercentage = minPaymentPercentage,
    minPaymentFloor = minPaymentFloor,
    interestRateEA = interestRateEA
)

fun CreditCardDto.toEntity(): CreditCardEntity = CreditCardEntity(
    id = id,
    accountId = accountId,
    creditLimit = creditLimit,
    currentDebt = currentDebt,
    cutoffDay = cutoffDay,
    paymentDueDay = paymentDueDay,
    minPaymentPercentage = minPaymentPercentage,
    minPaymentFloor = minPaymentFloor,
    interestRateEA = interestRateEA
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val budgetLimit: Double? = null,
    val isDefault: Boolean = false,
    val user_id: String? = null
)

fun CategoryEntity.toDto(): CategoryDto = CategoryDto(
    id = id,
    name = name,
    icon = icon,
    color = color,
    budgetLimit = budgetLimit,
    isDefault = isDefault
)

fun CategoryDto.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    icon = icon,
    color = color,
    budgetLimit = budgetLimit,
    isDefault = isDefault
)

@Serializable
data class TransactionDto(
    val id: String,
    val accountId: String?,
    val amount: Double,
    val type: String,
    val merchant: String?,
    val categoryId: String?,
    val rawNotification: String,
    val timestamp: Long,
    val confirmedByAI: Boolean = false,
    val needsReview: Boolean = false,
    val user_id: String? = null
)

fun TransactionEntity.toDto(): TransactionDto = TransactionDto(
    id = id,
    accountId = accountId,
    amount = amount,
    type = type.name,
    merchant = merchant,
    categoryId = categoryId,
    rawNotification = rawNotification,
    timestamp = timestamp,
    confirmedByAI = confirmedByAI,
    needsReview = needsReview
)

fun TransactionDto.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    accountId = accountId,
    amount = amount,
    type = TransactionType.entries.firstOrNull { it.name == type } ?: TransactionType.GASTO,
    merchant = merchant,
    categoryId = categoryId,
    rawNotification = rawNotification,
    timestamp = timestamp,
    confirmedByAI = confirmedByAI,
    needsReview = needsReview
)

@Serializable
data class MerchantCategoryMappingDto(
    val merchantKey: String,
    val categoryId: String,
    val updatedAt: Long,
    val user_id: String? = null
)

fun MerchantCategoryMappingEntity.toDto(): MerchantCategoryMappingDto = MerchantCategoryMappingDto(
    merchantKey = merchantKey,
    categoryId = categoryId,
    updatedAt = updatedAt
)

fun MerchantCategoryMappingDto.toEntity(): MerchantCategoryMappingEntity = MerchantCategoryMappingEntity(
    merchantKey = merchantKey,
    categoryId = categoryId,
    updatedAt = updatedAt
)

@Serializable
data class LoanDto(
    val id: String,
    val name: String,
    val totalAmount: Double,
    val remainingAmount: Double,
    val monthlyInterestRate: Double,
    val interestRateInputValue: Double,
    val interestRateType: String,
    val amortizationType: String,
    val totalInstallments: Int,
    val paidInstallments: Int = 0,
    val monthlyInstallmentAmount: Double,
    val fixedPrincipalAmount: Double = 0.0,
    val monthlyInsuranceAmount: Double = 0.0,
    val monthlyFeeAmount: Double = 0.0,
    val paymentDay: Int,
    val nextPaymentDate: Long,
    val linkedAccountId: String? = null,
    val createdAt: Long,
    val user_id: String? = null
)

fun LoanEntity.toDto(): LoanDto = LoanDto(
    id = id,
    name = name,
    totalAmount = totalAmount,
    remainingAmount = remainingAmount,
    monthlyInterestRate = monthlyInterestRate,
    interestRateInputValue = interestRateInputValue,
    interestRateType = interestRateType.name,
    amortizationType = amortizationType.name,
    totalInstallments = totalInstallments,
    paidInstallments = paidInstallments,
    monthlyInstallmentAmount = monthlyInstallmentAmount,
    fixedPrincipalAmount = fixedPrincipalAmount,
    monthlyInsuranceAmount = monthlyInsuranceAmount,
    monthlyFeeAmount = monthlyFeeAmount,
    paymentDay = paymentDay,
    nextPaymentDate = nextPaymentDate,
    linkedAccountId = linkedAccountId,
    createdAt = createdAt
)

fun LoanDto.toEntity(): LoanEntity = LoanEntity(
    id = id,
    name = name,
    totalAmount = totalAmount,
    remainingAmount = remainingAmount,
    monthlyInterestRate = monthlyInterestRate,
    interestRateInputValue = interestRateInputValue,
    interestRateType = LoanInterestRateType.entries.firstOrNull { it.name == interestRateType } ?: LoanInterestRateType.MONTHLY_EFFECTIVE,
    amortizationType = LoanAmortizationType.entries.firstOrNull { it.name == amortizationType } ?: LoanAmortizationType.FIXED_INSTALLMENT,
    totalInstallments = totalInstallments,
    paidInstallments = paidInstallments,
    monthlyInstallmentAmount = monthlyInstallmentAmount,
    fixedPrincipalAmount = fixedPrincipalAmount,
    monthlyInsuranceAmount = monthlyInsuranceAmount,
    monthlyFeeAmount = monthlyFeeAmount,
    paymentDay = paymentDay,
    nextPaymentDate = nextPaymentDate,
    linkedAccountId = linkedAccountId,
    createdAt = createdAt
)

@Serializable
data class LoanPaymentDto(
    val id: String,
    val loanId: String,
    val transactionId: String?,
    val installmentNumber: Int,
    val scheduledPaymentAmount: Double,
    val actualPaymentAmount: Double,
    val scheduledInsuranceAmount: Double = 0.0,
    val insurancePaidAmount: Double = 0.0,
    val unpaidInsuranceAmount: Double = 0.0,
    val scheduledFeeAmount: Double = 0.0,
    val feePaidAmount: Double = 0.0,
    val unpaidFeeAmount: Double = 0.0,
    val interestAccruedAmount: Double,
    val interestPaidAmount: Double,
    val unpaidInterestAmount: Double,
    val principalAmount: Double,
    val extraPrincipalAmount: Double = 0.0,
    val unappliedPaymentAmount: Double = 0.0,
    val remainingAmountBefore: Double,
    val remainingAmountAfter: Double,
    val paymentDate: Long,
    val createdAt: Long,
    val user_id: String? = null
)

fun LoanPaymentEntity.toDto(): LoanPaymentDto = LoanPaymentDto(
    id = id,
    loanId = loanId,
    transactionId = transactionId,
    installmentNumber = installmentNumber,
    scheduledPaymentAmount = scheduledPaymentAmount,
    actualPaymentAmount = actualPaymentAmount,
    scheduledInsuranceAmount = scheduledInsuranceAmount,
    insurancePaidAmount = insurancePaidAmount,
    unpaidInsuranceAmount = unpaidInsuranceAmount,
    scheduledFeeAmount = scheduledFeeAmount,
    feePaidAmount = feePaidAmount,
    unpaidFeeAmount = unpaidFeeAmount,
    interestAccruedAmount = interestAccruedAmount,
    interestPaidAmount = interestPaidAmount,
    unpaidInterestAmount = unpaidInterestAmount,
    principalAmount = principalAmount,
    extraPrincipalAmount = extraPrincipalAmount,
    unappliedPaymentAmount = unappliedPaymentAmount,
    remainingAmountBefore = remainingAmountBefore,
    remainingAmountAfter = remainingAmountAfter,
    paymentDate = paymentDate,
    createdAt = createdAt
)

fun LoanPaymentDto.toEntity(): LoanPaymentEntity = LoanPaymentEntity(
    id = id,
    loanId = loanId,
    transactionId = transactionId,
    installmentNumber = installmentNumber,
    scheduledPaymentAmount = scheduledPaymentAmount,
    actualPaymentAmount = actualPaymentAmount,
    scheduledInsuranceAmount = scheduledInsuranceAmount,
    insurancePaidAmount = insurancePaidAmount,
    unpaidInsuranceAmount = unpaidInsuranceAmount,
    scheduledFeeAmount = scheduledFeeAmount,
    feePaidAmount = feePaidAmount,
    unpaidFeeAmount = unpaidFeeAmount,
    interestAccruedAmount = interestAccruedAmount,
    interestPaidAmount = interestPaidAmount,
    unpaidInterestAmount = unpaidInterestAmount,
    principalAmount = principalAmount,
    extraPrincipalAmount = extraPrincipalAmount,
    unappliedPaymentAmount = unappliedPaymentAmount,
    remainingAmountBefore = remainingAmountBefore,
    remainingAmountAfter = remainingAmountAfter,
    paymentDate = paymentDate,
    createdAt = createdAt
)

@Serializable
data class DeferredPurchaseDto(
    val id: String,
    val creditCardId: String,
    val description: String,
    val totalAmount: Double,
    val totalInstallments: Int,
    val paidInstallments: Int,
    val purchaseDate: Long,
    val interestRateEA: Double? = null,
    val createdAt: Long,
    val user_id: String? = null
)

fun DeferredPurchaseEntity.toDto(): DeferredPurchaseDto = DeferredPurchaseDto(
    id = id,
    creditCardId = creditCardId,
    description = description,
    totalAmount = totalAmount,
    totalInstallments = totalInstallments,
    paidInstallments = paidInstallments,
    purchaseDate = purchaseDate,
    interestRateEA = interestRateEA,
    createdAt = createdAt
)

fun DeferredPurchaseDto.toEntity(): DeferredPurchaseEntity = DeferredPurchaseEntity(
    id = id,
    creditCardId = creditCardId,
    description = description,
    totalAmount = totalAmount,
    totalInstallments = totalInstallments,
    paidInstallments = paidInstallments,
    purchaseDate = purchaseDate,
    interestRateEA = interestRateEA,
    createdAt = createdAt
)

@Serializable
data class AssetDto(
    val id: String,
    val name: String,
    val amount: Double,
    val type: String,
    val createdAt: Long,
    val user_id: String? = null
)

fun AssetEntity.toDto(): AssetDto = AssetDto(
    id = id,
    name = name,
    amount = amount,
    type = type.name,
    createdAt = createdAt
)

fun AssetDto.toEntity(): AssetEntity = AssetEntity(
    id = id,
    name = name,
    amount = amount,
    type = AssetType.entries.firstOrNull { it.name == type } ?: AssetType.OTRO,
    createdAt = createdAt
)

@Serializable
data class CustomRuleDto(
    val id: String,
    val name: String,
    val regexPattern: String,
    val transactionType: String,
    val bankSource: String,
    val amountFormatType: Int,
    val createdAt: Long,
    val user_id: String? = null
)

fun CustomRuleEntity.toDto(): CustomRuleDto = CustomRuleDto(
    id = id,
    name = name,
    regexPattern = regexPattern,
    transactionType = transactionType,
    bankSource = bankSource,
    amountFormatType = amountFormatType,
    createdAt = createdAt
)

fun CustomRuleDto.toEntity(): CustomRuleEntity = CustomRuleEntity(
    id = id,
    name = name,
    regexPattern = regexPattern,
    transactionType = transactionType,
    bankSource = bankSource,
    amountFormatType = amountFormatType,
    createdAt = createdAt
)

@Serializable
data class NotificationSyncLedgerDto(
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
    val receivedAtMillis: Long,
    val status: String,
    val statusReason: String? = null,
    val transactionId: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val transactionType: String? = null,
    val amount: Double? = null,
    val merchant: String? = null,
    val bankSource: String? = null,
    val confidence: Double? = null,
    val classifierSource: String? = null,
    val errorMessage: String? = null,
    val processedAtMillis: Long? = null,
    val updatedAtMillis: Long,
    val user_id: String? = null
)

fun NotificationSyncLedgerEntity.toDto(): NotificationSyncLedgerDto = NotificationSyncLedgerDto(
    id = id,
    packageName = packageName,
    title = title,
    text = text,
    postedAtMillis = postedAtMillis,
    receivedAtMillis = receivedAtMillis,
    status = status.name,
    statusReason = statusReason,
    transactionId = transactionId,
    accountId = accountId,
    categoryId = categoryId,
    transactionType = transactionType,
    amount = amount,
    merchant = merchant,
    bankSource = bankSource,
    confidence = confidence,
    classifierSource = classifierSource,
    errorMessage = errorMessage,
    processedAtMillis = processedAtMillis,
    updatedAtMillis = updatedAtMillis
)

fun NotificationSyncLedgerDto.toEntity(): NotificationSyncLedgerEntity = NotificationSyncLedgerEntity(
    id = id,
    packageName = packageName,
    title = title,
    text = text,
    postedAtMillis = postedAtMillis,
    receivedAtMillis = receivedAtMillis,
    status = NotificationProcessingStatus.entries.firstOrNull { it.name == status } ?: NotificationProcessingStatus.RECEIVED,
    statusReason = statusReason,
    transactionId = transactionId,
    accountId = accountId,
    categoryId = categoryId,
    transactionType = transactionType,
    amount = amount,
    merchant = merchant,
    bankSource = bankSource,
    confidence = confidence,
    classifierSource = classifierSource,
    errorMessage = errorMessage,
    processedAtMillis = processedAtMillis,
    updatedAtMillis = updatedAtMillis
)

@Serializable
data class PaymentMatchSuggestionDto(
    val id: String,
    val sourceTransactionId: String,
    val targetType: String,
    val targetId: String,
    val targetName: String,
    val expectedAmount: Double,
    val actualAmount: Double,
    val differenceAmount: Double,
    val confidence: Double,
    val reason: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null,
    val acceptedApplicationId: String? = null,
    val user_id: String? = null
)

fun PaymentMatchSuggestionEntity.toDto(): PaymentMatchSuggestionDto = PaymentMatchSuggestionDto(
    id = id,
    sourceTransactionId = sourceTransactionId,
    targetType = targetType.name,
    targetId = targetId,
    targetName = targetName,
    expectedAmount = expectedAmount,
    actualAmount = actualAmount,
    differenceAmount = differenceAmount,
    confidence = confidence,
    reason = reason,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
    acceptedApplicationId = acceptedApplicationId
)

fun PaymentMatchSuggestionDto.toEntity(): PaymentMatchSuggestionEntity = PaymentMatchSuggestionEntity(
    id = id,
    sourceTransactionId = sourceTransactionId,
    targetType = PaymentMatchTargetType.entries.firstOrNull { it.name == targetType }
        ?: PaymentMatchTargetType.CREDIT_CARD,
    targetId = targetId,
    targetName = targetName,
    expectedAmount = expectedAmount,
    actualAmount = actualAmount,
    differenceAmount = differenceAmount,
    confidence = confidence,
    reason = reason,
    status = PaymentMatchStatus.entries.firstOrNull { it.name == status }
        ?: PaymentMatchStatus.PENDING,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
    acceptedApplicationId = acceptedApplicationId
)

@Serializable
data class DebtPaymentApplicationDto(
    val id: String,
    val sourceTransactionId: String,
    val suggestionId: String? = null,
    val targetType: String,
    val targetId: String,
    val targetName: String,
    val amount: Double,
    val expectedAmount: Double,
    val differenceAmount: Double,
    val applicationType: String,
    val appliedAt: Long,
    val user_id: String? = null
)

fun DebtPaymentApplicationEntity.toDto(): DebtPaymentApplicationDto = DebtPaymentApplicationDto(
    id = id,
    sourceTransactionId = sourceTransactionId,
    suggestionId = suggestionId,
    targetType = targetType.name,
    targetId = targetId,
    targetName = targetName,
    amount = amount,
    expectedAmount = expectedAmount,
    differenceAmount = differenceAmount,
    applicationType = applicationType.name,
    appliedAt = appliedAt
)

fun DebtPaymentApplicationDto.toEntity(): DebtPaymentApplicationEntity = DebtPaymentApplicationEntity(
    id = id,
    sourceTransactionId = sourceTransactionId,
    suggestionId = suggestionId,
    targetType = PaymentMatchTargetType.entries.firstOrNull { it.name == targetType }
        ?: PaymentMatchTargetType.CREDIT_CARD,
    targetId = targetId,
    targetName = targetName,
    amount = amount,
    expectedAmount = expectedAmount,
    differenceAmount = differenceAmount,
    applicationType = DebtPaymentApplicationType.entries.firstOrNull { it.name == applicationType }
        ?: DebtPaymentApplicationType.CARD_EXTRA_PAYMENT,
    appliedAt = appliedAt
)

@Serializable
data class FinancialAdjustmentDto(
    val id: String,
    val targetType: String,
    val targetId: String,
    val targetName: String,
    val previousValue: Double,
    val newValue: Double,
    val delta: Double,
    val reason: String,
    val note: String? = null,
    val createdAt: Long,
    val user_id: String? = null
)

fun FinancialAdjustmentEntity.toDto(): FinancialAdjustmentDto = FinancialAdjustmentDto(
    id = id,
    targetType = targetType.name,
    targetId = targetId,
    targetName = targetName,
    previousValue = previousValue,
    newValue = newValue,
    delta = delta,
    reason = reason,
    note = note,
    createdAt = createdAt
)

fun FinancialAdjustmentDto.toEntity(): FinancialAdjustmentEntity = FinancialAdjustmentEntity(
    id = id,
    targetType = FinancialAdjustmentTargetType.entries.firstOrNull { it.name == targetType }
        ?: FinancialAdjustmentTargetType.ACCOUNT_BALANCE,
    targetId = targetId,
    targetName = targetName,
    previousValue = previousValue,
    newValue = newValue,
    delta = delta,
    reason = reason,
    note = note,
    createdAt = createdAt
)
