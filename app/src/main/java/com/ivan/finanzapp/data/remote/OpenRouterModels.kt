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
    @Json(name = "response_format") val responseFormat: ResponseFormat? = ResponseFormat()
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

/**
 * Estructura JSON que le pedimos al LLM que devuelva. Coincide con el
 * prompt definido en [com.ivan.finanzapp.data.remote.TransactionAiClassifier].
 */
@JsonClass(generateAdapter = true)
data class AiClassificationResult(
    val tipo: String,              // "INGRESO" | "GASTO" | "GASTO_TC" | "TRANSFERENCIA" | "PAGO_TC"
    val monto: Double,
    val comercio: String?,
    @Json(name = "categoria_sugerida") val categoriaSugerida: String?,
    val confianza: Double
)
