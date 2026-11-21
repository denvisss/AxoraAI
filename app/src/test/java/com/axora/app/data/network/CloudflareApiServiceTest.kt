package com.axora.app.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CloudflareApiServiceTest {

    private lateinit var capturedRequest: Request
    private var capturedBody: String = ""
    private var responseBody =
        """{"result":{"image":"AQID"},"success":true,"errors":[],"messages":[]}"""

    private val service = Retrofit.Builder()
        .baseUrl("https://api.cloudflare.com/")
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
                        .body(responseBody.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CloudflareApiService::class.java)

    @Test
    fun generationPostsMultipartToKleinEndpoint() = runBlocking {
        val url = "https://api.cloudflare.com/client/v4/accounts/acc123/ai/run/@cf/black-forest-labs/flux-2-klein-4b"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", "a red cat")
            .addFormDataPart("width", "1024")
            .addFormDataPart("height", "1024")
            .addFormDataPart("seed", "42")
            .build()

        val response = service.runKlein(url, "Bearer test-placeholder", body)

        assertEquals("AQID", response.result?.image)
        assertTrue(response.success == true)

        assertEquals("POST", capturedRequest.method)
        assertEquals("api.cloudflare.com", capturedRequest.url.host)
        assertTrue(capturedRequest.url.encodedPath.endsWith("/ai/run/@cf/black-forest-labs/flux-2-klein-4b"))
        assertTrue(capturedRequest.url.encodedPath.contains("/accounts/acc123/"))
        assertEquals("Bearer test-placeholder", capturedRequest.header("Authorization"))

        val multipart = capturedRequest.body as MultipartBody
        val partNames = multipart.parts.mapNotNull { part ->
            part.headers?.get("Content-Disposition")
                ?.substringAfter("name=\"")?.substringBefore("\"")
        }
        assertEquals(listOf("prompt", "width", "height", "seed"), partNames)
        assertTrue(capturedBody.contains("a red cat"))
        assertTrue(capturedBody.contains("1024"))
        assertTrue(capturedBody.contains("42"))
    }

    @Test
    fun editBodyCarriesInputImagePart() = runBlocking {
        val url = "https://api.cloudflare.com/client/v4/accounts/acc123/ai/run/@cf/black-forest-labs/flux-2-klein-4b"
        val image = MultipartBody.Part.createFormData(
            "input_image_0",
            "source.jpg",
            byteArrayOf(1, 2, 3).toRequestBody("image/jpeg".toMediaType())
        )
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", "make the sky sunset")
            .addPart(image)
            .addFormDataPart("width", "800")
            .addFormDataPart("height", "608")
            .build()

        service.runKlein(url, "Bearer test-placeholder", body)

        val multipart = capturedRequest.body as MultipartBody
        val partNames = multipart.parts.mapNotNull { part ->
            part.headers?.get("Content-Disposition")
                ?.substringAfter("name=\"")?.substringBefore("\"")
        }
        assertEquals(listOf("prompt", "input_image_0", "width", "height"), partNames)
        val imagePart = multipart.parts[1]
        assertTrue(imagePart.headers?.get("Content-Disposition")!!.contains("filename=\"source.jpg\""))
        assertEquals("image/jpeg", imagePart.body.contentType().toString())
        assertTrue(capturedBody.contains("make the sky sunset"))
    }

    @Test
    fun parsesNestedCloudflareErrorsArray() = runBlocking {
        responseBody = """{"success":false,"errors":[[7301,"Model requires a valid token"]],"messages":[]}"""

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", "p")
            .build()
        val response = service.runKlein(
            "https://api.cloudflare.com/client/v4/accounts/acc123/ai/run/@cf/black-forest-labs/flux-2-klein-4b",
            "Bearer test-placeholder",
            body
        )

        assertTrue(response.success == false)
        val first = response.errors?.firstOrNull() as? List<*>
        assertEquals("Model requires a valid token", first?.get(1))
    }
}
