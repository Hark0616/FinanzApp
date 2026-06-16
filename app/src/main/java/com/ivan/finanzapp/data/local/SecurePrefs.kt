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

    companion object {
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
    }
}
