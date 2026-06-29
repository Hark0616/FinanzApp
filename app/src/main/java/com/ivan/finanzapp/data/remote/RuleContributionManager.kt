package com.ivan.finanzapp.data.remote

import com.ivan.finanzapp.data.local.entity.CustomRuleEntity
import com.ivan.finanzapp.data.notification.parsers.ParsedTransaction
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleContributionManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authManager: SupabaseAuthManager
) {
    private val postgrestModule = supabaseClient.postgrest

    suspend fun contributeValidatedRule(
        rule: CustomRuleEntity,
        packageName: String,
        parsedTransaction: ParsedTransaction,
        transactionIdHash: String,
        classifierSource: String,
        validatedAtMillis: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = authManager.currentUser?.id ?: return@withContext Result.success(Unit)
            val dto = RuleContributionDto(
                id = rule.id,
                ruleId = rule.id,
                name = rule.name,
                regexPattern = rule.regexPattern,
                transactionType = rule.transactionType,
                bankSource = rule.bankSource,
                amountFormatType = rule.amountFormatType,
                validatedPackageName = packageName,
                validatedTransactionType = parsedTransaction.type.name,
                validatedBankSource = parsedTransaction.source.name,
                classifierSource = classifierSource,
                transactionIdHash = transactionIdHash,
                validatedAtMillis = validatedAtMillis,
                createdAt = rule.createdAt,
                contributor_user_id = userId
            )
            postgrestModule.from("rule_contributions").insert(dto)
            Result.success(Unit)
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            if (message.contains("duplicate", ignoreCase = true) || message.contains("23505")) {
                Result.success(Unit)
            } else {
                Result.failure(error)
            }
        }
    }
}
