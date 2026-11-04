package com.imageai.app.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface TextApiService {

    /**
     * Текстовый API для перевода и генерации текста.
     * Пример: GET /chat?message=Translate%20to%20English%3A%20Привет&model=openai
     */
    @GET("chat")
    suspend fun translateText(
        @Query("message") message: String,
        @Query("model") model: String = "openai",
        @Query("temperature") temperature: Double = 0.3,
        @Query("max_tokens") maxTokens: Int = 500,
    ): TranslateResponse
}

data class TranslateResponse(
    @SerializedName("message") val message: String
)
