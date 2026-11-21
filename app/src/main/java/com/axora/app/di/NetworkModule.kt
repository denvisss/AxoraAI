package com.axora.app.di

import com.axora.app.BuildConfig
import com.axora.app.data.network.CloudflareApiService
import com.axora.app.data.network.GitHubApiService
import com.axora.app.data.network.TextApiService
import com.axora.app.data.network.TranslationClient
import com.google.gson.Gson
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
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val cloudflareRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.cloudflare.com/")
            .client(baseClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val groqRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .client(baseClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val gitHubRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(baseClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val httpClient: OkHttpClient get() = baseClient

    val cloudflareApiService: CloudflareApiService by lazy {
        cloudflareRetrofit.create(CloudflareApiService::class.java)
    }

    val textApiService: TextApiService by lazy { groqRetrofit.create(TextApiService::class.java) }

    val translationClient: TranslationClient by lazy { TranslationClient(baseClient, gson) }

    val gitHubApiService: GitHubApiService by lazy {
        gitHubRetrofit.create(GitHubApiService::class.java)
    }
}
