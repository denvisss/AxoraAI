package com.axora.app.data.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface AudioApiService {

    @GET("audio/{prompt}")
    suspend fun generateAudio(
        @Path("prompt") prompt: String,
        @Header("Authorization") authorization: String?,
    ): ResponseBody
}
