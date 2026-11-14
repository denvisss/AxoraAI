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
import java.io.File
import java.util.Base64

class ImageApiServiceTest {

    private lateinit var capturedRequest: Request
    private var capturedBody: String = ""

    private val service = Retrofit.Builder()
        .baseUrl("https://image.pollinations.ai/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    capturedRequest = chain.request()
                    capturedBody = capturedRequest.body?.let { body ->
                        Buffer().also { body.writeTo(it) }.readUtf8()
                    }.orEmpty()
                    val payload = if (capturedRequest.url.encodedPath.endsWith("/v1/images/edits")) {
                        """{"data":[{"b64_json":"AQID"}]}""".toResponseBody("application/json".toMediaType())
                    } else {
                        byteArrayOf(1, 2, 3).toResponseBody("image/png".toMediaType())
                    }
                    Response.Builder()
                        .request(capturedRequest)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(payload)
                        .build()
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ImageApiService::class.java)

    @Test
    fun highResolutionUsesModernEndpointAndBearerHeader() = runBlocking {
        val prompt = "кот / фото & солнце?#"
        service.generateHighResolutionImage(
            prompt,
            "Bearer test-placeholder",
            seed = 42,
            model = "black-forest-labs/flux.2-klein-4b",
            width = 1536,
            height = 1536
        ).use {
            assertEquals(listOf<Byte>(1, 2, 3), it.bytes().toList())
        }

        val url = capturedRequest.url
        assertEquals("https", url.scheme)
        assertEquals("gen.pollinations.ai", url.host)
        assertEquals(listOf("image", prompt), url.pathSegments)
        assertEquals("black-forest-labs/flux.2-klein-4b", url.queryParameter("model"))
        assertEquals("1536", url.queryParameter("width"))
        assertEquals("1536", url.queryParameter("height"))
        assertEquals("42", url.queryParameter("seed"))
        assertEquals("Bearer test-placeholder", capturedRequest.header("Authorization"))
        assertNull(url.queryParameter("key"))
        assertNull(url.fragment)
    }

    @Test
    fun anonymousGenerationKeepsClassicEndpointWithoutCredentials() = runBlocking {
        service.generateImage("red apple", seed = 7).close()

        val url = capturedRequest.url
        assertEquals("image.pollinations.ai", url.host)
        assertEquals(listOf("prompt", "red apple"), url.pathSegments)
        assertEquals("7", url.queryParameter("seed"))
        assertEquals("flux", url.queryParameter("model"))
        assertEquals("768", url.queryParameter("width"))
        assertEquals("768", url.queryParameter("height"))
        assertNull(url.queryParameter("key"))
        assertNull(capturedRequest.header("Authorization"))
    }

    @Test
    fun editImagePostsSourceAsDataUriAndDecodesBase64Result() = runBlocking {
        val source = File.createTempFile("axora-edit", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val response = service.editImage(
            "Bearer test-placeholder",
            ImageEditBody(
                file = source,
                mime = "image/png",
                prompt = "make the sky sunset",
                model = "black-forest-labs/flux.2-klein-4b",
                size = "1536x1536"
            )
        )
        assertEquals("AQID", response.data?.firstOrNull()?.b64Json)

        val url = capturedRequest.url
        assertEquals("POST", capturedRequest.method)
        assertEquals("gen.pollinations.ai", url.host)
        assertEquals("/v1/images/edits", url.encodedPath)
        assertEquals("Bearer test-placeholder", capturedRequest.header("Authorization"))
        assertTrue(capturedBody.contains("\"prompt\":\"make the sky sunset\""))
        assertTrue(capturedBody.contains("\"image_url\":\"data:image/png;base64,AQID\""))
        assertTrue(capturedBody.contains("\"response_format\":\"b64_json\""))
        assertTrue(capturedBody.contains("\"model\":\"black-forest-labs/flux.2-klein-4b\""))
        assertTrue(capturedBody.contains("\"size\":\"1536x1536\""))
    }

    @Test
    fun editBodyStreamsBase64CorrectlyAcrossChunkBoundaries() = runBlocking {
        val bytes = ByteArray(3 * 4096 * 2 + 7) { (it % 251).toByte() }
        val source = File.createTempFile("axora-edit-big", ".jpg").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        service.editImage(
            "Bearer test-placeholder",
            ImageEditBody(source, "image/jpeg", "p", "m", "auto")
        )

        val expected = Base64.getEncoder().encodeToString(bytes)
        val start = capturedBody.indexOf(";base64,") + ";base64,".length
        val end = capturedBody.indexOf("\"}]", start)
        assertEquals(expected, capturedBody.substring(start, end))
    }
}