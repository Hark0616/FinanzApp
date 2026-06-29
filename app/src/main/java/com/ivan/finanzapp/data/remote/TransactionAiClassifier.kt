package com.ivan.finanzapp.data.remote

import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.notification.parsers.ParsedTransaction
import com.ivan.finanzapp.data.security.SecureLog
import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback de clasificación con IA (Nivel 3 del pipeline de categorización).
 *
 * Se invoca cuando el [com.ivan.finanzapp.data.notification.parsers.ParserDispatcher]
 * no logra extraer datos de una notificación con reglas locales (regex).
 *
 * Usa OpenRouter con un modelo económico/rápido (por defecto Gemini Flash)
 * para extraer en una sola llamada: tipo de transacción, monto, comercio
 * y categoría sugerida, todo en JSON estructurado.
 *
 * Si el usuario no ha configurado su API key de OpenRouter (ver
 * [SecurePrefs.getOpenRouterApiKey]), este clasificador simplemente
 * devuelve null y la transacción queda marcada como `needsReview = true`
 * para revisión manual.
 */
@Singleton
class TransactionAiClassifier @Inject constructor(
    private val openRouterApi: OpenRouterApi,
    private val securePrefs: SecurePrefs
) {

    private var resolvedDefaultModel: String? = null

    /**
     * Fallback conocido si el catálogo de OpenRouter no se puede consultar.
     * En ejecución normal se resuelve dinámicamente contra /models para evitar
     * quedarse anclados a un id obsoleto.
     */
    private val fallbackModel = "google/gemini-3.1-flash-lite"

    private val systemPrompt = """
        Eres un asistente que analiza notificaciones de aplicaciones bancarias
        colombianas (Davivienda, Nequi, Daviplata u otros bancos) y extrae
        información estructurada.

        Responde SIEMPRE y ÚNICAMENTE con un objeto JSON válido, sin texto
        adicional, sin explicaciones, sin markdown, con esta forma exacta:
        {
          "tipo": "INGRESO" | "GASTO" | "GASTO_TC" | "TRANSFERENCIA" | "PAGO_TC",
          "monto": <numero, sin simbolos de moneda ni separadores de miles>,
          "comercio": "<nombre del comercio, persona o entidad, o null>",
          "categoria_sugerida": "Mercado" | "Transporte" | "Restaurantes" | "Suscripciones" | "Salud" | "Servicios públicos" | "Hogar" | "Entretenimiento" | "Educación" | "Ingresos" | "Pago tarjeta de crédito" | "Otros",
          "confianza": <numero entre 0.0 y 1.0 indicando tu certeza>
        }

        Reglas:
        - "GASTO_TC" es solo para compras con tarjeta de crédito (deuda).
        - "GASTO" es para pagos/compras desde cuentas de ahorro, Nequi o Daviplata (dinero propio).
        - "PAGO_TC" es cuando el usuario paga/abona su tarjeta de crédito.
        - "TRANSFERENCIA" es para envíos de dinero a otra persona/cuenta (no compras).
        - "INGRESO" es para dinero recibido (transferencias recibidas, recargas, giros, salario).
        - Si no puedes determinar el monto con certeza, usa confianza baja (menor a 0.5).
    """.trimIndent()

    /**
     * Intenta clasificar el texto de una notificación.
     *
     * @return [ParsedTransaction] si la llamada fue exitosa y el modelo
     *         devolvió un JSON válido, o null si no hay API key configurada,
     *         hubo un error de red, o la respuesta no se pudo interpretar.
     */
    suspend fun classify(packageName: String, title: String, text: String): ParsedTransaction? {
        return classifyWithCategory(packageName, title, text)?.first
    }

    /**
     * Devuelve además la categoría sugerida por el LLM (separado de
     * [ParsedTransaction] porque la categoría no forma parte del modelo
     * de parsing por reglas, sino del [com.ivan.finanzapp.domain.usecase.CategoryResolver]).
     */
    suspend fun classifyWithCategory(
        packageName: String,
        title: String,
        text: String
    ): Pair<ParsedTransaction, String?>? {
        return when (securePrefs.getCloudAiProvider()) {
            SecurePrefs.CLOUD_PROVIDER_SUPABASE_EDGE -> {
                classifyWithSupabaseEdge(packageName, title, text)
            }
            else -> {
                classifyWithOpenRouterDirect(packageName, title, text)
            }
        }
    }

    private suspend fun classifyWithSupabaseEdge(
        packageName: String,
        title: String,
        text: String
    ): Pair<ParsedTransaction, String?>? {
        SecureLog.i(
            "TransactionAiClassifier",
            "Cloud AI provider SUPABASE_EDGE selected, but Edge Function is not wired yet. Falling back to OpenRouter direct."
        )
        return classifyWithOpenRouterDirect(packageName, title, text)
    }

    private suspend fun classifyWithOpenRouterDirect(
        packageName: String,
        title: String,
        text: String
    ): Pair<ParsedTransaction, String?>? {
        val apiKey = securePrefs.getOpenRouterApiKey() ?: return null

        val userPrompt = """
            Notificación de la app con paquete "$packageName":
            Título: "$title"
            Texto: "$text"
        """.trimIndent()

        val request = OpenRouterRequest(
            model = resolveDefaultModel(),
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            )
        )

        return try {
            val response = openRouterApi.chatCompletion("Bearer $apiKey", request)
            if (!response.isSuccessful) {
                SecureLog.w("TransactionAiClassifier", "OpenRouter classify failed: HTTP ${response.code()}")
                return null
            }

            val content = response.body()?.choices?.firstOrNull()?.message?.content ?: return null
            val result = parseAiJson(content) ?: return null

            val parsed = ParsedTransaction(
                type = mapTipo(result.tipo),
                amount = result.monto,
                merchant = result.comercio,
                availableBalance = null,
                source = BankSource.DESCONOCIDO,
                confidence = result.confianza
            )
            parsed to result.categoriaSugerida
        } catch (e: Exception) {
            SecureLog.w("TransactionAiClassifier", "OpenRouter classifyWithCategory failed.", e)
            null
        }
    }

    private fun parseAiJson(content: String): AiClassificationResult? {
        return try {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            val adapter = moshi.adapter(AiClassificationResult::class.java)
            // El modelo a veces envuelve el JSON en ```json ... ```; lo limpiamos
            val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            adapter.fromJson(cleaned)
        } catch (e: Exception) {
            SecureLog.w("TransactionAiClassifier", "AI JSON parsing failed.", e)
            null
        }
    }

    private fun mapTipo(tipo: String): TransactionType {
        return TransactionType.entries.firstOrNull { it.name == tipo } ?: TransactionType.GASTO
    }

    private suspend fun resolveDefaultModel(): String {
        resolvedDefaultModel?.let { return it }

        val selected = runCatching {
            val response = openRouterApi.models()
            if (!response.isSuccessful) return@runCatching null
            response.body()?.data
                ?.let(::selectBestGeminiFlashModel)
        }.onFailure {
            SecureLog.w("TransactionAiClassifier", "OpenRouter model discovery failed.", it)
        }.getOrNull()

        return (selected ?: fallbackModel).also {
            resolvedDefaultModel = it
            SecureLog.i("TransactionAiClassifier", "Using OpenRouter model: $it")
        }
    }

    private fun selectBestGeminiFlashModel(models: List<OpenRouterModelInfo>): String? {
        val candidates = models
            .asSequence()
            .filterNot { it.reasoning?.mandatory == true }
            .map { it.id }
            .filter { it.startsWith("google/gemini-") }
            .filter {
                it.contains("flash", ignoreCase = true) ||
                        it.contains("lite", ignoreCase = true) ||
                        it.contains("nano", ignoreCase = true)
            }
            .filterNot {
                it.contains("image", ignoreCase = true) ||
                        it.contains("vision", ignoreCase = true)
            }
            .map {
                ModelCandidate(
                    id = it,
                    version = extractGeminiVersion(it),
                    isPreview = it.contains("preview", ignoreCase = true) ||
                            it.contains("experimental", ignoreCase = true)
                )
            }
            .filter { it.version.isNotEmpty() }
            .toList()

        return candidates
            .filterNot { it.isPreview }
            .maxWithOrNull(modelCandidateComparator)
            ?.id
            ?: candidates.maxWithOrNull(modelCandidateComparator)?.id
    }

    private fun extractGeminiVersion(modelId: String): List<Int> {
        val match = Regex("""google/gemini-(\d+(?:\.\d+)*)""").find(modelId) ?: return emptyList()
        return match.groupValues[1].split(".").mapNotNull { it.toIntOrNull() }
    }

    private val modelCandidateComparator = Comparator<ModelCandidate> { left, right ->
        compareVersions(left.version, right.version)
    }

    private fun compareVersions(left: List<Int>, right: List<Int>): Int {
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val comparison = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private data class ModelCandidate(
        val id: String,
        val version: List<Int>,
        val isPreview: Boolean
    )
}
