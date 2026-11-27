package com.axora.app.data.network

import com.google.gson.Gson
import okhttp3.ResponseBody
import java.util.Base64

// Модели Workers AI отвечают по-разному: FLUX и Lucid Origin — JSON с base64
// в result.image, Phoenix — сырой JPEG. Возвращаем байты картинки или null.
object ImageResponseDecoder {

    private val gson = Gson()

    fun decode(body: ResponseBody): ByteArray? = body.use {
        if (it.contentType()?.type == "image") {
            it.bytes()
        } else {
            gson.fromJson(it.string(), KleinGenerationResponse::class.java)
                ?.result?.image?.takeIf { encoded -> encoded.isNotBlank() }
                ?.let { encoded -> Base64.getDecoder().decode(encoded) }
        }
    }
}
