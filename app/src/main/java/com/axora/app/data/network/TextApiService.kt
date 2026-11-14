package com.axora.app.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

const val KEYED_TEXT_URL = "https://gen.pollinations.ai/v1/chat/completions"

data class ChatMessageRequest(
    val model: String = "openai",
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 500
)

data class Message(
    val role: String = "user",
    val content: String
)

data class ChatMessageResponse(
    @SerializedName("choices") val choices: List<Choice>? = null
)

data class Choice(
    val message: MessageContent? = null
)

data class MessageContent(
    val content: String? = null
)

interface TextApiService {

    @POST("openai")
    suspend fun generateText(@Body request: ChatMessageRequest): ChatMessageResponse

    @POST
    suspend fun generateTextKeyed(
        @Url url: String,
        @Body request: ChatMessageRequest,
        @Header("Authorization") authorization: String
    ): ChatMessageResponse
}