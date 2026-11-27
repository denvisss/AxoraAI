package com.axora.app.data.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

data class KleinGenerationResponse(
    val result: KleinImageResult?,
    val success: Boolean? = null,
    val errors: List<Any>? = null,
    val messages: List<Any>? = null
)

data class KleinImageResult(val image: String?)

interface CloudflareApiService {

    // Модели отвечают по-разному: FLUX и Lucid Origin — JSON с base64 в result.image,
    // Phoenix — сырой JPEG. Поэтому тело разбираем сами, а не через Gson-конвертер.
    @POST
    suspend fun runKlein(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: RequestBody
    ): Response<ResponseBody>
}
