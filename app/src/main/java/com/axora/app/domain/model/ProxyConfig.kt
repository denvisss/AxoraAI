package com.axora.app.domain.model

import com.google.gson.annotations.SerializedName
import java.net.InetSocketAddress
import java.net.Proxy

data class ProxyConfig(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("scheme") val scheme: String,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int
) {
    fun toJavaProxy(): Proxy {
        val type = if (scheme == SCHEME_SOCKS4 || scheme == SCHEME_SOCKS5) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress(host, port))
    }

    val label: String get() = "${scheme.uppercase()} $host:$port"

    companion object {
        const val SCHEME_HTTP = "http"
        const val SCHEME_HTTPS = "https"
        const val SCHEME_SOCKS4 = "socks4"
        const val SCHEME_SOCKS5 = "socks5"
        val SCHEMES = listOf(SCHEME_SOCKS5, SCHEME_SOCKS4, SCHEME_HTTP, SCHEME_HTTPS)
    }
}
