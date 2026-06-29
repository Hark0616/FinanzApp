package com.ivan.finanzapp.data.remote

import android.content.Context
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.notification.parsers.ParsedTransaction
import com.ivan.finanzapp.data.security.SecureLog
import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAiClassifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val securePrefs: SecurePrefs
) {

    private val generativeModel by lazy { Generation.getClient() }
    private val jsonAdapter by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
            .adapter(AiClassificationResult::class.java)
    }

    private val systemPrompt = """
        Analiza notificaciones bancarias colombianas y devuelve solo JSON valido.
        No uses markdown, no expliques nada y no agregues texto fuera del JSON.

        Forma exacta:
        {
          "tipo": "INGRESO" | "GASTO" | "GASTO_TC" | "TRANSFERENCIA" | "PAGO_TC",
          "monto": <numero sin simbolos ni separadores de miles>,
          "comercio": "<nombre del comercio, persona o entidad, o null>",
          "categoria_sugerida": "Mercado" | "Transporte" | "Restaurantes" | "Suscripciones" | "Salud" | "Servicios publicos" | "Hogar" | "Entretenimiento" | "Educacion" | "Ingresos" | "Pago tarjeta de credito" | "Otros",
          "confianza": <numero entre 0.0 y 1.0>
        }

        Reglas:
        - GASTO_TC solo si es compra con tarjeta de credito.
        - GASTO si es pago/compra desde cuenta, Nequi o Daviplata.
        - PAGO_TC si el usuario paga o abona una tarjeta de credito.
        - TRANSFERENCIA si es envio de dinero a otra persona/cuenta, no compra.
        - INGRESO si el usuario recibe dinero.
        - Si falta el monto o no hay transaccion, usa confianza menor a 0.5.
    """.trimIndent()

    /**
     * Verifica si el dispositivo físico actual soporta ejecución de IA local (Gemini Nano).
     * Retorna verdadero si es un S26 Ultra, S25, S24, Pixel 8+ o si el paquete com.google.android.aicore existe.
     */
    fun isLocalAiSupported(): Boolean {
        val model = Build.MODEL ?: ""
        val brand = Build.BRAND ?: ""
        
        // Dispositivos conocidos con NPU y soporte oficial de Gemini Nano
        val isPremiumDevice = model.contains("S26", ignoreCase = true) ||
                model.contains("S25", ignoreCase = true) ||
                model.contains("S24", ignoreCase = true) ||
                model.contains("Pixel 8", ignoreCase = true) ||
                model.contains("Pixel 9", ignoreCase = true) ||
                model.contains("Pixel 10", ignoreCase = true) ||
                brand.contains("Google", ignoreCase = true) ||
                // Detectar modelos reales de Samsung flagships (SM-S9xxx para la serie S22+ y SM-F9xxx para Fold/Flip)
                (brand.contains("samsung", ignoreCase = true) && (
                    model.contains("SM-S9", ignoreCase = true) ||
                    model.contains("SM-F9", ignoreCase = true)
                ))

        val hasAiCorePackage = try {
            context.packageManager.getPackageInfo("com.google.android.aicore", 0) != null
        } catch (e: Exception) {
            false
        }

        return isPremiumDevice || hasAiCorePackage
    }

    /**
     * Indica si el usuario tiene habilitado el uso de IA local en la configuración.
     */
    fun isLocalAiEnabled(): Boolean {
        return securePrefs.isLocalAiEnabled() && isLocalAiSupported()
    }

    fun shouldDeferUntilForeground(): Boolean {
        return isLocalAiEnabled() && !isAppInForeground()
    }

    /**
     * Intenta clasificar la notificacion con Gemini Nano local via ML Kit / AI Core.
     * Si el feature no esta disponible o la plataforma rechaza la inferencia
     * (por ejemplo, cuando la app no esta en primer plano), devuelve null para
     * que el procesador pueda usar el siguiente fallback configurado.
     */
    suspend fun classifyLocally(
        packageName: String,
        title: String,
        text: String
    ): Pair<ParsedTransaction, String?>? {
        if (!isLocalAiEnabled()) return null
        if (!isAppInForeground()) {
            SecureLog.i("LocalAiClassifier", "Skipping Gemini Nano inference because app is not in foreground.")
            return null
        }

        return try {
            ensureFeatureAvailable()
            generateTransaction(packageName, title, text)
        } catch (e: Exception) {
            if (e.isBackgroundUseBlocked()) {
                throw LocalAiForegroundRequiredException(e)
            }
            SecureLog.w("LocalAiClassifier", "Gemini Nano local classification failed.", e)
            null
        }
    }

    suspend fun runConnectionTest(
        packageName: String,
        title: String,
        text: String
    ): LocalAiConnectionTestResult {
        val supported = isLocalAiSupported()
        val appInForeground = isAppInForeground()
        val modelName = runCatching { generativeModel.getBaseModelName() }
            .onFailure { SecureLog.w("LocalAiClassifier", "Failed to read Gemini Nano base model.", it) }
            .getOrNull()

        val initialStatus = runCatching { generativeModel.checkStatus() }
            .onFailure { SecureLog.w("LocalAiClassifier", "Failed to check Gemini Nano feature status.", it) }
            .getOrNull()

        val parsed = if (appInForeground) {
            runCatching {
                ensureFeatureAvailable(initialStatus)
                generateTransaction(packageName, title, text)
            }.onFailure {
                SecureLog.w("LocalAiClassifier", "Gemini Nano connection test failed.", it)
            }.getOrNull()
        } else {
            SecureLog.i("LocalAiClassifier", "Gemini Nano connection test skipped inference: appInForeground=false.")
            null
        }

        val finalStatus = runCatching { generativeModel.checkStatus() }.getOrNull()
        val result = LocalAiConnectionTestResult(
            supported = supported,
            enabled = securePrefs.isLocalAiEnabled(),
            appInForeground = appInForeground,
            initialFeatureStatus = initialStatus?.toFeatureStatusName(),
            finalFeatureStatus = finalStatus?.toFeatureStatusName(),
            baseModelName = modelName,
            parsedTransaction = parsed?.first,
            suggestedCategory = parsed?.second
        )
        SecureLog.i(
            "LocalAiClassifier",
            "Gemini Nano connection test: supported=${result.supported}, enabled=${result.enabled}, appInForeground=${result.appInForeground}, initialStatus=${result.initialFeatureStatus}, finalStatus=${result.finalFeatureStatus}, model=${result.baseModelName}, parsed=${result.parsedTransaction}, category=${result.suggestedCategory}"
        )
        return result
    }

    private suspend fun ensureFeatureAvailable(
        knownStatus: Int? = null
    ) {
        val status = knownStatus ?: generativeModel.checkStatus()
        SecureLog.i("LocalAiClassifier", "Gemini Nano feature status=${status.toFeatureStatusName()}")
        when (status) {
            FeatureStatus.AVAILABLE -> return
            FeatureStatus.UNAVAILABLE -> error("Gemini Nano feature unavailable on this device/account.")
            else -> downloadFeature()
        }
    }

    private suspend fun downloadFeature() {
        SecureLog.i("LocalAiClassifier", "Downloading Gemini Nano feature if needed.")
        generativeModel.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> {
                    SecureLog.i("LocalAiClassifier", "Gemini Nano download started: bytes=${status.bytesToDownload}")
                }
                is DownloadStatus.DownloadProgress -> {
                    SecureLog.d("LocalAiClassifier", "Gemini Nano download progress: bytes=${status.totalBytesDownloaded}")
                }
                is DownloadStatus.DownloadCompleted -> {
                    SecureLog.i("LocalAiClassifier", "Gemini Nano download completed.")
                }
                is DownloadStatus.DownloadFailed -> {
                    throw status.e
                }
            }
        }
    }

    private suspend fun generateTransaction(
        packageName: String,
        title: String,
        text: String
    ): Pair<ParsedTransaction, String?>? {
        val request = generateContentRequest(
            TextPart(
                """
                    Paquete Android: "$packageName"
                    Titulo: "$title"
                    Texto: "$text"
                """.trimIndent()
            )
        ) {
            promptPrefix = PromptPrefix(systemPrompt)
            temperature = 0.1f
            topK = 1
            maxOutputTokens = 160
            candidateCount = 1
        }

        val content = generativeModel.generateContent(request)
            .candidates
            .firstOrNull()
            ?.text
            ?.trim()
            ?: return null

        SecureLog.d("LocalAiClassifier", "Gemini Nano raw response: ${content.take(300)}")
        val result = parseAiJson(content) ?: return null
        if (result.monto <= 0 || result.confianza < 0.5) return null

        val parsed = ParsedTransaction(
            type = mapTipo(result.tipo),
            amount = result.monto,
            merchant = result.comercio,
            availableBalance = null,
            source = BankSource.DESCONOCIDO,
            confidence = result.confianza
        )
        return parsed to result.categoriaSugerida
    }

    private fun parseAiJson(content: String): AiClassificationResult? {
        return try {
            val cleaned = content
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            jsonAdapter.fromJson(cleaned)
        } catch (e: Exception) {
            SecureLog.w("LocalAiClassifier", "Gemini Nano JSON parsing failed.", e)
            null
        }
    }

    private fun mapTipo(tipo: String): TransactionType {
        return TransactionType.entries.firstOrNull { it.name == tipo } ?: TransactionType.GASTO
    }

    fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

    private fun Int.toFeatureStatusName(): String = when (this) {
        FeatureStatus.AVAILABLE -> "AVAILABLE"
        FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
        else -> "DOWNLOAD_REQUIRED_OR_IN_PROGRESS($this)"
    }

    private fun Throwable.isBackgroundUseBlocked(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            val message = current.message.orEmpty()
            if (
                message.contains("BACKGROUND_USE_BLOCKED", ignoreCase = true) ||
                message.contains("Background usage is blocked", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}

class LocalAiForegroundRequiredException(
    cause: Throwable
) : RuntimeException("Gemini Nano requires FinanzApp to be in foreground.", cause)

data class LocalAiConnectionTestResult(
    val supported: Boolean,
    val enabled: Boolean,
    val appInForeground: Boolean,
    val initialFeatureStatus: String?,
    val finalFeatureStatus: String?,
    val baseModelName: String?,
    val parsedTransaction: ParsedTransaction?,
    val suggestedCategory: String?
)
