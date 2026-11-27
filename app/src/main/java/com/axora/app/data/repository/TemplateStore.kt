package com.axora.app.data.repository

import android.content.Context
import com.axora.app.domain.model.PromptTemplate
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

class TemplateStore(context: Context) {

    private val file = File(context.filesDir, "prompt_templates.json")
    private val gson = Gson()

    // Читаем терпимо: файлы, записанные до фикса R8, содержат обфусцированные
    // ключи (a/b вместо id/text), поэтому разбираем оба варианта вручную.
    @Synchronized
    fun load(): List<PromptTemplate> = runCatching {
        if (!file.exists()) return emptyList()
        JsonParser.parseString(file.readText()).asJsonArray.mapNotNull { element ->
            val obj = element.asJsonObject
            val text = obj.string("text") ?: obj.string("b") ?: return@mapNotNull null
            val id = obj.long("id") ?: obj.long("a") ?: 0L
            PromptTemplate(id = id, text = text)
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(templates: List<PromptTemplate>) {
        runCatching {
            file.writeText(gson.toJson(templates))
        }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asLong
}
