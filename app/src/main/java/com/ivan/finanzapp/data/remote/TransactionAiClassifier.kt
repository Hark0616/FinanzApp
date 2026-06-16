package com.ivan.finanzapp.data.remote

import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.notification.parsers.ParsedTransaction
import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
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

    /**
     * Modelo por defecto: rápido y económico, suficiente para esta tarea
     * de clasificación simple. El usuario puede cambiarlo desde Ajustes
     * (no implementado en este MVP, pero la arquitectura lo permite).
     */
    private val defaultModel = "google/gemini-flash-1.5"

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
        val apiKey = securePrefs.getOpenRouterApiKey() ?: return null

        val userPrompt = """
            Notificación de la app con paquete "$packageName":
            Título: "$title"
            Texto: "$text"
        """.trimIndent()

        val request = OpenRouterRequest(
            model = defaultModel,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            )
        )

        return try {
            val response = openRouterApi.chatCompletion("Bearer $apiKey", request)
            if (!response.isSuccessful) return null

            val content = response.body()?.choices?.firstOrNull()?.message?.content ?: return null
            val result = parseAiJson(content) ?: return null

            ParsedTransaction(
                type = mapTipo(result.tipo),
                amount = result.monto,
                merchant = result.comercio,
                availableBalance = null,
                source = BankSource.DESCONOCIDO,
                confidence = result.confianza
            )
        } catch (e: Exception) {
            null
        }
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
        val apiKey = securePrefs.getOpenRouterApiKey() ?: return null

        val userPrompt = """
            Notificación de la app con paquete "$packageName":
            Título: "$title"
            Texto: "$text"
        """.trimIndent()

        val request = OpenRouterRequest(
            model = defaultModel,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            )
        )

        return try {
            val response = openRouterApi.chatCompletion("Bearer $apiKey", request)
            if (!response.isSuccessful) return null

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
            null
        }
    }

    private fun parseAiJson(content: String): AiClassificationResult? {
        return try {
            val moshi = com.squareup.moshi.Moshi.Builder().build()
            val adapter = moshi.adapter(AiClassificationResult::class.java)
            // El modelo a veces envuelve el JSON en ```json ... ```; lo limpiamos
            val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            adapter.fromJson(cleaned)
        } catch (e: Exception) {
            null
        }
    }

    private fun mapTipo(tipo: String): TransactionType {
        return TransactionType.entries.firstOrNull { it.name == tipo } ?: TransactionType.GASTO
    }
}
