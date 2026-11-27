package com.axora.app.domain.model

import com.google.gson.annotations.SerializedName

data class PromptTemplate(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("text") val text: String
) {
    val placeholders: List<String>
        get() = PLACEHOLDER.findAll(text).map { it.groupValues[1] }.toList().distinct()

    fun fill(values: Map<String, String>): String =
        PLACEHOLDER.replace(text) { match ->
            values[match.groupValues[1]] ?: match.value
        }

    companion object {
        val PLACEHOLDER = Regex("""\[([^\[\]]+)]""")
    }
}
