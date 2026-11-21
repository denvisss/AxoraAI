package com.axora.app.data.network

import okhttp3.RequestBody
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

    @POST
    suspend fun runKlein(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: RequestBody
    ): KleinGenerationResponse
}
