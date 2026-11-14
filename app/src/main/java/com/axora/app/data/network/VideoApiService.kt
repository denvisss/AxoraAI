package com.axora.app.data.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface VideoApiService {

    @GET("video/{prompt}")
    suspend fun generateVideo(
        @Path("prompt") prompt: String,
        @Header("Authorization") authorization: String?,
    ): ResponseBody
}
