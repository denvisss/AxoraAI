package com.imageai.app.data.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Url

interface ImageApiService {

    /**
     * Полный URL формируется в repository.
     * Пример: https://gen.pollinations.ai/image/a%20beautiful%20sunset?width=1024&height=1024&seed=-1&model=flux&key=xxx
     */
    @GET
    suspend fun generateImage(
        @Url fullUrl: String,
    ): ResponseBody
}
