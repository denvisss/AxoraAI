package com.imageai.app.di

import android.content.Context
import com.imageai.app.data.network.ImageApiService
import com.imageai.app.data.network.TextApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val IMAGE_POLLINATIONS_BASE_URL = "https://gen.pollinations.ai/"
private const val TEXT_POLLINATIONS_BASE_URL = "https://text.pollinations.ai/"

object NetworkModule {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val imageClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val textClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val imageRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://gen.pollinations.ai/")
            .client(imageClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val textRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(TEXT_POLLINATIONS_BASE_URL)
            .client(textClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val imageApiService: ImageApiService by lazy {
        imageRetrofit.create(ImageApiService::class.java)
    }

    val textApiService: TextApiService by lazy {
        textRetrofit.create(TextApiService::class.java)
    }
}

fun initNetwork(context: Context) {
    // Pre-warm network modules
    NetworkModule.imageApiService
}

fun provideImageApiService(): ImageApiService = NetworkModule.imageApiService
fun provideTextApiService(): TextApiService = NetworkModule.textApiService
