package com.axora.app.domain.model

// Текстовые модели Groq. Порядок в catalog — порядок в селекторе: сверху самая мощная.
data class TextModel(
    val id: String,
    val displayName: String,
    val description: String
) {
    companion object {
        private val GPT_120B = TextModel(
            id = "openai/gpt-oss-120b",
            displayName = "GPT-OSS 120B",
            description = "Самая умная"
        )
        private val GPT_20B = TextModel(
            id = "openai/gpt-oss-20b",
            displayName = "GPT-OSS 20B",
            description = "Заметно быстрее"
        )
        private val LLAMA_70B = TextModel(
            id = "llama-3.3-70b-versatile",
            displayName = "Llama 3.3 70B",
            description = "Универсальная альтернатива"
        )

        val catalog = listOf(GPT_120B, GPT_20B, LLAMA_70B)

        fun byId(id: String): TextModel? = catalog.firstOrNull { it.id == id }
    }
}
