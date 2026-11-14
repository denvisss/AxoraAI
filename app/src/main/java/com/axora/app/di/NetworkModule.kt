package com.axora.app.di

import com.google.gson.Gson
import com.axora.app.BuildConfig
import com.axora.app.data.network.AudioApiService
import com.axora.app.data.network.ImageApiService
import com.axora.app.data.network.TextApiService
import com.axora.app.data.network.TranslationClient
import com.axora.app.data.network.VideoApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val gson: Gson = Gson()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        redactHeader("Authorization")
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val longClient: OkHttpClient = baseClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val imageRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://image.pollinations.ai/")
            .client(longClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val mediaRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://gen.pollinations.ai/")
            .client(longClient)
            .build()
    }

    private val textRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://text.pollinations.ai/")
            .client(baseClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val imageApiService: ImageApiService by lazy { imageRetrofit.create(ImageApiService::class.java) }
    val videoApiService: VideoApiService by lazy { mediaRetrofit.create(VideoApiService::class.java) }
    val audioApiService: AudioApiService by lazy { mediaRetrofit.create(AudioApiService::class.java) }
    val textApiService: TextApiService by lazy { textRetrofit.create(TextApiService::class.java) }
    val translationClient: TranslationClient by lazy { TranslationClient(baseClient, gson) }
}