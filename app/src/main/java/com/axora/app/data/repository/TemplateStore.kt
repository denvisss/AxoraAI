package com.axora.app.data.repository

import android.content.Context
import com.axora.app.domain.model.PromptTemplate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class TemplateStore(context: Context) {

    private val file = File(context.filesDir, "prompt_templates.json")
    private val gson = Gson()

    @Synchronized
    fun load(): List<PromptTemplate> = runCatching {
        if (!file.exists()) return emptyList()
        val type = object : TypeToken<List<PromptTemplate>>() {}.type
        gson.fromJson<List<PromptTemplate>>(file.readText(), type).orEmpty()
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(templates: List<PromptTemplate>) {
        runCatching {
            file.writeText(gson.toJson(templates))
        }
    }
}
