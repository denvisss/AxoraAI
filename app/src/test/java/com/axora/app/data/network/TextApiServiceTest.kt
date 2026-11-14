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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TextApiServiceTest {

    private lateinit var capturedRequest: Request
    private var capturedBody: String = ""

    private val service = Retrofit.Builder()
        .baseUrl("https://text.pollinations.ai/")
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
    fun classicTextPostsToOpenaiEndpoint() = runBlocking {
        val response = service.generateText(
            ChatMessageRequest(
                messages = listOf(Message(role = "user", content = "привет")),
                max_tokens = 1000
            )
        )
        assertEquals("Привет!", response.choices?.firstOrNull()?.message?.content)

        assertEquals("POST", capturedRequest.method)
        assertEquals("text.pollinations.ai", capturedRequest.url.host)
        assertEquals("/openai", capturedRequest.url.encodedPath)
        assertNull(capturedRequest.header("Authorization"))
        assertTrue(capturedBody.contains("\"model\":\"openai\""))
        assertTrue(capturedBody.contains("\"max_tokens\":1000"))
        assertTrue(capturedBody.contains("\"content\":\"привет\""))
    }

    @Test
    fun keyedTextUsesFullUrlAndBearerHeader() = runBlocking {
        val response = service.generateTextKeyed(
            url = KEYED_TEXT_URL,
            request = ChatMessageRequest(
                model = "qwen3guard-8b",
                messages = listOf(Message(role = "user", content = "hi"))
            ),
            authorization = "Bearer test-placeholder"
        )
        assertEquals("Привет!", response.choices?.firstOrNull()?.message?.content)

        assertEquals(KEYED_TEXT_URL, capturedRequest.url.toString())
        assertEquals("Bearer test-placeholder", capturedRequest.header("Authorization"))
        assertTrue(capturedBody.contains("\"model\":\"qwen3guard-8b\""))
    }
}