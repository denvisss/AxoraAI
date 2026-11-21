package com.axora.app.data.network

import com.google.gson.Gson
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
import java.io.IOException

class TranslationClientTest {

    private var capturedRequest: Request? = null
    private var capturedBody: String = ""
    private var responseCode = 200
    private var responseBody = """{"choices":[{"message":{"content":"a red cat"}}]}"""
    private var failWithIOException = false

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            capturedRequest = chain.request()
            capturedBody = chain.request().body?.let { body ->
                Buffer().also { body.writeTo(it) }.readUtf8()
            }.orEmpty()
            if (failWithIOException) throw IOException("offline")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message("OK")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private val translator = TranslationClient(client, Gson())

    @Test
    fun latinTextSkipsNetwork() = runBlocking {
        assertEquals("red cat", translator.translateToEnglish("red cat", groqApiKey = "test-placeholder"))
        assertNull(capturedRequest)
    }

    @Test
    fun cyrillicWithoutKeySkipsNetwork() = runBlocking {
        assertEquals("рыжий кот", translator.translateToEnglish("рыжий кот", groqApiKey = null))
        assertNull(capturedRequest)
    }

    @Test
    fun cyrillicWithKeyPostsToGroqChatCompletions() = runBlocking {
        assertEquals("a red cat", translator.translateToEnglish("рыжий кот", groqApiKey = "test-placeholder"))

        val request = capturedRequest!!
        assertEquals("api.groq.com", request.url.host)
        assertEquals("/openai/v1/chat/completions", request.url.encodedPath)
        assertEquals("Bearer test-placeholder", request.header("Authorization"))
        assertTrue(capturedBody.contains("\"model\":\"openai/gpt-oss-120b\""))
        assertTrue(capturedBody.contains("\"role\":\"system\""))
        assertTrue(capturedBody.contains("\"content\":\"рыжий кот\""))
        assertTrue(capturedBody.contains("\"reasoning_effort\":\"low\""))
    }

    @Test
    fun emptyModelContentFallsBackToOriginalText() = runBlocking {
        responseBody = """{"choices":[{"message":{"content":""}}]}"""
        assertEquals("рыжий кот", translator.translateToEnglish("рыжий кот", groqApiKey = "test-placeholder"))
    }

    @Test
    fun rawJsonBodyIsNeverUsedAsTranslation() = runBlocking {
        responseBody = """{"error":{"message":"boom"}}"""
        assertEquals("рыжий кот", translator.translateToEnglish("рыжий кот", groqApiKey = "test-placeholder"))
    }

    @Test
    fun httpErrorFallsBackToOriginalText() = runBlocking {
        responseCode = 500
        assertEquals("рыжий кот", translator.translateToEnglish("рыжий кот", groqApiKey = "test-placeholder"))
    }

    @Test
    fun plainTextResponseIsUsedAsTranslation() = runBlocking {
        responseBody = "  a red cat  "
        assertEquals("a red cat", translator.translateToEnglish("рыжий кот", groqApiKey = "test-placeholder"))
    }

    @Test
    fun networkFailureFallsBackToOriginalText() = runBlocking {
        failWithIOException = true
        assertEquals("рыжий кот", translator.translateToEnglish("рыжий кот", groqApiKey = "test-placeholder"))
    }
}
