package com.ivan.finanzapp.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Modelos de request/response para la API de OpenRouter
 * (compatible con el formato de OpenAI Chat Completions).
 */

@JsonClass(generateAdapter = true)
data class OpenRouterRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,
    @param:Json(name = "max_tokens") val maxTokens: Int = 160,
    @param:Json(name = "include_reasoning") val includeReasoning: Boolean = false,
    @param:Json(name = "response_format") val responseFormat: ResponseFormat? = ResponseFormat()
)

@JsonClass(generateAdapter = true)
data class ResponseFormat(
    val type: String = "json_object"
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenRouterResponse(
    val choices: List<Choice>
)

@JsonClass(generateAdapter = true)
data class Choice(
    val message: ChatMessage
)

@JsonClass(generateAdapter = true)
data class OpenRouterModelsResponse(
    val data: List<OpenRouterModelInfo>
)

@JsonClass(generateAdapter = true)
data class OpenRouterModelInfo(
    val id: String,
    val name: String? = null,
    val reasoning: OpenRouterModelReasoning? = null
)

@JsonClass(generateAdapter = true)
data class OpenRouterModelReasoning(
    val mandatory: Boolean? = null,
    @param:Json(name = "default_enabled") val defaultEnabled: Boolean? = null
)

/**
 * Estructura JSON que le pedimos al LLM que devuelva. Coincide con el
 * prompt definido en [com.ivan.finanzapp.data.remote.TransactionAiClassifier].
 */
@JsonClass(generateAdapter = true)
data class AiClassificationResult(
    val tipo: String,              // "INGRESO" | "GASTO" | "GASTO_TC" | "TRANSFERENCIA" | "PAGO_TC"
    val monto: Double,
    val comercio: String?,
    @param:Json(name = "categoria_sugerida") val categoriaSugerida: String?,
    val confianza: Double
)
