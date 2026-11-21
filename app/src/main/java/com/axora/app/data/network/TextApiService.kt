package com.axora.app.data.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class ChatMessageRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val stream: Boolean? = null,
    val reasoning_effort: String? = null
)

data class Message(val role: String = "user", val content: String)

data class ChatMessageResponse(val choices: List<Choice>?)

data class Choice(val message: MessageContent?)

data class MessageContent(val content: String?)

interface TextApiService {

    @POST("openai/v1/chat/completions")
    suspend fun chatCompletions(
        @Body request: ChatMessageRequest,
        @Header("Authorization") authorization: String
    ): ChatMessageResponse
}
