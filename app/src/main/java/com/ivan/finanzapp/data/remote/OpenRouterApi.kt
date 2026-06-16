package com.ivan.finanzapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Endpoint de OpenRouter, compatible con el formato de OpenAI.
 * Base URL: https://openrouter.ai/api/v1/
 */
interface OpenRouterApi {

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String, // "Bearer sk-or-..."
        @Body request: OpenRouterRequest
    ): Response<OpenRouterResponse>
}
