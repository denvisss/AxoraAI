package com.axora.app.data.network

import com.google.gson.annotations.SerializedName
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class ImageEditResponse(
    val data: List<EditedImage>? = null
)

data class EditedImage(
    @SerializedName("b64_json") val b64Json: String? = null
)

interface ImageApiService {

    @GET("prompt/{prompt}")
    suspend fun generateImage(
        @Path("prompt") prompt: String,
        @Query("seed") seed: Int,
        @Query("model") model: String = "flux",
        @Query("width") width: Int = 768,
        @Query("height") height: Int = 768,
    ): ResponseBody

    @GET("https://gen.pollinations.ai/image/{prompt}")
    suspend fun generateHighResolutionImage(
        @Path("prompt") prompt: String,
        @Header("Authorization") authorization: String,
        @Query("seed") seed: Int,
        @Query("model") model: String,
        @Query("width") width: Int,
        @Query("height") height: Int,
    ): ResponseBody

    @POST("https://gen.pollinations.ai/v1/images/edits")
    suspend fun editImage(
        @Header("Authorization") authorization: String,
        @Body body: RequestBody
    ): ImageEditResponse
}