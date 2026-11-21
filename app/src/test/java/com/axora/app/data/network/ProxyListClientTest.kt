package com.axora.app.data.network

import com.google.gson.Gson
import com.axora.app.domain.model.ProxyConfig
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

class ProxyListClientTest {

    private val client = ProxyListClient(OkHttpClient(), Gson())

    private val sampleJson = """
        [
          {"proxy":"http://1.1.1.1:8080","protocol":"http","ip":"1.1.1.1","port":8080,"https":false,"anonymity":"elite","score":95,"geolocation":{"country":"US","city":"Alexandria"}},
          {"proxy":"http://2.2.2.2:3128","protocol":"http","ip":"2.2.2.2","port":3128,"https":false,"anonymity":"anonymous","score":10,"geolocation":{"country":"DE","city":"Berlin"}},
          {"proxy":"http://3.3.3.3:80","protocol":"http","ip":"3.3.3.3","port":80,"https":false,"anonymity":"transparent","score":0,"geolocation":{"country":"FR","city":"Paris"}},
          {"proxy":"http://bad","protocol":"http","ip":"bad","port":70000,"https":false,"anonymity":"elite","score":99,"geolocation":{"country":"US","city":"X"}},
          {"proxy":"http://4.4.4.4:8080","protocol":"http","ip":"1.1.1.1","port":8080,"https":false,"anonymity":"anonymous","score":50,"geolocation":{"country":"GB","city":"London"}}
        ]
    """.trimIndent()

    @Test
    fun `parse maps entries and filters invalid`() {
        val entries = client.parse(sampleJson)
        assertEquals(4, entries.size)
        val first = entries.first { it.ip == "1.1.1.1" }
        assertEquals(8080, first.port)
        assertEquals("http", first.protocol)
        assertEquals("elite", first.anonymity)
        assertEquals("US", first.country)
        assertEquals("Alexandria", first.city)
    }

    @Test
    fun `parse invalid json returns empty`() {
        assertTrue(client.parse("not json").isEmpty())
    }

    @Test
    fun `normalize dedupes sorts by score and anonymity and limits`() {
        val parsed = client.parse(sampleJson)
        val normalized = ProxyListClient.normalize(parsed)
        assertEquals(listOf("1.1.1.1", "2.2.2.2", "3.3.3.3"), normalized.map { it.ip })
        assertEquals(2, ProxyListClient.normalize(parsed, limit = 2).size)
    }

    @Test
    fun `load maps response body`() {
        val interceptorClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val entries = runBlocking { ProxyListClient(interceptorClient, Gson()).load("http") }
        assertEquals(3, entries.size)
    }
}

class ProxyConfigTest {

    @Test
    fun `socks schemes map to socks proxy`() {
        val proxy = ProxyConfig(true, ProxyConfig.SCHEME_SOCKS5, "10.0.0.1", 1080).toJavaProxy()
        assertEquals(Proxy.Type.SOCKS, proxy.type())
        assertEquals(InetSocketAddress("10.0.0.1", 1080), proxy.address())
    }

    @Test
    fun `http schemes map to http proxy`() {
        listOf(ProxyConfig.SCHEME_HTTP, ProxyConfig.SCHEME_HTTPS).forEach { scheme ->
            val proxy = ProxyConfig(false, scheme, "proxy.example.com", 3128).toJavaProxy()
            assertEquals(Proxy.Type.HTTP, proxy.type())
            assertEquals(InetSocketAddress("proxy.example.com", 3128), proxy.address())
        }
    }

    @Test
    fun `label shows scheme host and port`() {
        assertEquals("SOCKS5 10.0.0.1:1080", ProxyConfig(true, "socks5", "10.0.0.1", 1080).label)
    }
}
