package com.axora.app.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TextApiServiceTest {

    private lateinit var capturedRequest: Request
    private var capturedBody: String = ""

    private val service = Retrofit.Builder()
        .baseUrl("https://api.groq.com/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    capturedBody = capturedRequest.body?.let { body ->
                        Buffer().also { body.writeTo(it) }.readUtf8()
                    }.orEmpty()
                    Response.Builder()
                        .request(capturedRequest)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """{"choices":[{"message":{"content":"Привет!"}}]}"""
                                .toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TextApiService::class.java)

    @Test
    fun chatCompletionsPostsToGroqWithBearer() = runBlocking {
        val response = service.chatCompletions(
            ChatMessageRequest(
                model = "openai/gpt-oss-120b",
                messages = listOf(Message(role = "user", content = "привет")),
                temperature = 0.7,
                max_tokens = 1000
            ),
            authorization = "Bearer test-placeholder"
        )
        assertEquals("Привет!", response.choices?.firstOrNull()?.message?.content)

        assertEquals("POST", capturedRequest.method)
        assertEquals("api.groq.com", capturedRequest.url.host)
        assertEquals("/openai/v1/chat/completions", capturedRequest.url.encodedPath)
        assertEquals("Bearer test-placeholder", capturedRequest.header("Authorization"))
        assertTrue(capturedBody.contains("\"model\":\"openai/gpt-oss-120b\""))
        assertTrue(capturedBody.contains("\"max_tokens\":1000"))
        assertTrue(capturedBody.contains("\"temperature\":0.7"))
        assertTrue(capturedBody.contains("\"content\":\"привет\""))
    }
}
