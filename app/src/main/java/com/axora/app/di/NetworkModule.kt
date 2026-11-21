package com.axora.app.di

import com.axora.app.BuildConfig
import com.axora.app.data.network.CloudflareApiService
import com.axora.app.data.network.GitHubApiService
import com.axora.app.data.network.ProxyListClient
import com.axora.app.data.network.TextApiService
import com.axora.app.data.network.TranslationClient
import com.axora.app.domain.model.ProxyConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val PROXY_TEST_URL = "https://www.cloudflare.com/cdn-cgi/trace"

    private val gson: Gson = Gson()

    val proxyConfig = MutableStateFlow<ProxyConfig?>(null)

    private val dynamicProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val config = proxyConfig.value
            return if (config != null && config.enabled) {
                listOf(config.toJavaProxy())
            } else {
                listOf(Proxy.NO_PROXY)
            }
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }

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
        .proxySelector(dynamicProxySelector)
        .build()

    val directClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .proxySelector(ProxySelector.getDefault())
            .build()
    }

    suspend fun testProxy(config: ProxyConfig, timeoutMs: Long = 10_000L): Long =
        withContext(Dispatchers.IO) {
            val client = baseClient.newBuilder()
                .proxy(config.toJavaProxy())
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val started = System.currentTimeMillis()
            client.newCall(Request.Builder().url(PROXY_TEST_URL).build()).execute().use { response ->
                val code = response.code
                if (code < 200 || code >= 500) error("HTTP $code")
            }
            System.currentTimeMillis() - started
        }

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

    val proxyListClient: ProxyListClient by lazy { ProxyListClient(directClient, gson) }

    val cloudflareApiService: CloudflareApiService by lazy {
        cloudflareRetrofit.create(CloudflareApiService::class.java)
    }

    val textApiService: TextApiService by lazy { groqRetrofit.create(TextApiService::class.java) }

    val translationClient: TranslationClient by lazy { TranslationClient(baseClient, gson) }

    val gitHubApiService: GitHubApiService by lazy {
        gitHubRetrofit.create(GitHubApiService::class.java)
    }
}
