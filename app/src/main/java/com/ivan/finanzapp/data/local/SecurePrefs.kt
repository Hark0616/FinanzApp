package com.ivan.finanzapp.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacenamiento seguro para:
 * - La passphrase con la que se cifra la base de datos Room (SQLCipher).
 * - La API key de OpenRouter (usada para el fallback de clasificación con IA).
 *
 * Usa [EncryptedSharedPreferences], que cifra tanto las claves como los
 * valores usando una MasterKey gestionada por el Android Keystore.
 */
@Singleton
class SecurePrefs @Inject constructor(
    context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "finanzapp_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Obtiene la passphrase de la base de datos, generándola la primera
     * vez con un generador criptográficamente seguro (32 bytes).
     */
    fun getOrCreateDbPassphrase(): ByteArray {
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) {
            return existing.split(",").map { it.toByte() }.toByteArray()
        }
        val newPassphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_DB_PASSPHRASE, newPassphrase.joinToString(","))
            .apply()
        return newPassphrase
    }

    fun getOpenRouterApiKey(): String? = prefs.getString(KEY_OPENROUTER_API_KEY, null)

    fun setOpenRouterApiKey(apiKey: String) {
        prefs.edit().putString(KEY_OPENROUTER_API_KEY, apiKey).apply()
    }

    fun clearOpenRouterApiKey() {
        prefs.edit().remove(KEY_OPENROUTER_API_KEY).apply()
    }

    fun isLocalAiEnabled(): Boolean = prefs.getBoolean(KEY_LOCAL_AI_ENABLED, false)

    fun setLocalAiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_AI_ENABLED, enabled).apply()
    }

    fun getNotificationProcessingMode(): String {
        return prefs.getString(KEY_PROCESSING_MODE, MODE_PARSER) ?: MODE_PARSER
    }

    fun setNotificationProcessingMode(mode: String) {
        prefs.edit().putString(KEY_PROCESSING_MODE, mode).apply()
    }

    fun getLastCloudSyncAt(): Long = prefs.getLong(KEY_LAST_CLOUD_SYNC_AT, 0L)

    fun setLastCloudSyncAt(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_CLOUD_SYNC_AT, timestampMillis).apply()
    }

    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }

    companion object {
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        private const val KEY_LOCAL_AI_ENABLED = "local_ai_enabled"
        private const val KEY_PROCESSING_MODE = "processing_mode"
        private const val KEY_LAST_CLOUD_SYNC_AT = "last_cloud_sync_at"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"

        const val MODE_PARSER = "PARSER"
        const val MODE_LOCAL_AI = "LOCAL_AI"
        const val MODE_CLOUD_AI = "CLOUD_AI"
    }
}
