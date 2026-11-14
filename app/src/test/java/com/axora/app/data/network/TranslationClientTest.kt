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
        assertEquals("red cat", translator.translateToEnglish("red cat"))
        assertNull(capturedRequest)
    }

    @Test
    fun cyrillicTextUsesClassicEndpointWithoutKey() = runBlocking {
        assertEquals("a red cat", translator.translateToEnglish("рыжий кот"))

        val request = capturedRequest!!
        assertEquals("text.pollinations.ai", request.url.host)
        assertNull(request.header("Authorization"))
        assertTrue(capturedBody.contains("\"content\":\"рыжий кот\""))
        assertTrue(capturedBody.contains("\"role\":\"system\""))
    }

    @Test
    fun cyrillicTextWithKeyUsesKeyedEndpointAndBearer() = runBlocking {
        assertEquals("a red cat", translator.translateToEnglish("рыжий кот", apiKey = "test-placeholder"))

        val request = capturedRequest!!
        assertEquals(KEYED_TEXT_URL, request.url.toString())
        assertEquals("Bearer test-placeholder", request.header("Authorization"))
    }

    @Test
    fun httpErrorFallsBackToOriginalText() = runBlocking {
        responseCode = 500
        assertEquals("рыжий кот", translator.translateToEnglish("рыжий кот"))
    }

    @Test
    fun plainTextResponseIsUsedAsTranslation() = runBlocking {
        responseBody = "  a red cat  "
        assertEquals("a red cat", translator.translateToEnglish("рыжий кот"))
    }

    @Test
    fun networkFailureFallsBackToOriginalText() = runBlocking {
        failWithIOException = true
        assertEquals("рыжий кот", translator.translateToEnglish("рыжий кот"))
    }
}