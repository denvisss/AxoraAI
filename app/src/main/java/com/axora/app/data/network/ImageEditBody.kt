package com.axora.app.data.network

import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.util.Base64

/**
 * Streams the source image as base64 inside the JSON payload so the whole
 * file never lives in memory as a String (a 1536² edit would otherwise
 * allocate several duplicate multi-MB buffers).
 */
class ImageEditBody(
    private val file: File,
    private val mime: String,
    private val prompt: String,
    private val model: String,
    private val size: String
) : RequestBody() {

    private val gson = Gson()

    override fun contentType(): MediaType = JSON

    override fun contentLength(): Long = -1

    override fun writeTo(sink: BufferedSink) {
        sink.writeUtf8("{\"prompt\":")
        sink.writeUtf8(gson.toJson(prompt))
        sink.writeUtf8(",\"image\":[{\"image_url\":\"data:")
        sink.writeUtf8(mime)
        sink.writeUtf8(";base64,")

        val encoder = Base64.getEncoder()
        val chunk = ByteArray(3 * 4096)
        file.inputStream().buffered().use { input ->
            while (true) {
                var filled = 0
                while (filled < chunk.size) {
                    val read = input.read(chunk, filled, chunk.size - filled)
                    if (read == -1) break
                    filled += read
                }
                if (filled == 0) break
                sink.write(encoder.encode(if (filled == chunk.size) chunk else chunk.copyOf(filled)))
                if (filled < chunk.size) break
            }
        }

        sink.writeUtf8("\"}],\"model\":")
        sink.writeUtf8(gson.toJson(model))
        sink.writeUtf8(",\"size\":")
        sink.writeUtf8(gson.toJson(size))
        sink.writeUtf8(",\"response_format\":\"b64_json\"}")
    }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}