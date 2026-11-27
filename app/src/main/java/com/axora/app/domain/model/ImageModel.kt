package com.axora.app.domain.model

// Модели генерации картинок на Cloudflare Workers AI. Порядок в catalog — порядок в селекторе:
// сверху самая мощная, она же по умолчанию.
//
// NB: 20.11.2026 Cloudflare удалил старые модели (SDXL, Dreamshaper, SDXL-Lightning,
// SD1.5-inpainting — теперь HTTP 410 Gone / код 5028). Их id — в excludedIds:
// живой каталог с API может отдавать их ещё какое-то время, в селектор они попадать не должны.
data class ImageModel(
    val id: String,
    val displayName: String,
    val description: String
) {
    companion object {
        private val KLEIN_9B = ImageModel(
            id = "@cf/black-forest-labs/flux-2-klein-9b",
            displayName = "FLUX.2 Klein 9B",
            description = "Klein 9B — мощнее и чуть медленнее"
        )
        private val KLEIN_4B = ImageModel(
            id = "@cf/black-forest-labs/flux-2-klein-4b",
            displayName = "FLUX.2 Klein 4B",
            description = "Быстрая, по умолчанию"
        )
        private val FLUX_2_DEV = ImageModel(
            id = "@cf/black-forest-labs/flux-2-dev",
            displayName = "FLUX.2 Dev",
            description = "Максимальное качество, медленная"
        )
        private val FLUX_1_SCHNELL = ImageModel(
            id = "@cf/black-forest-labs/flux-1-schnell",
            displayName = "FLUX.1 Schnell",
            description = "Очень быстрая, 12B"
        )
        val DEFAULT = KLEIN_4B

        // Запасная модель для правки по референсу, когда выбрана модель без поддержки картинки на входе.
        val EDIT_DEFAULT = KLEIN_9B

        val catalog = listOf(
            KLEIN_9B,
            KLEIN_4B,
            FLUX_2_DEV,
            FLUX_1_SCHNELL
        )

        // Эти модели принимают input_image_0 (правка по референсу).
        private val editingIds = setOf(KLEIN_9B.id, KLEIN_4B.id, FLUX_2_DEV.id)

        // Не показываем в селекторе: снятые с производства 20.11.2026 модели
        // (Workers AI отвечает на них HTTP 410) и Leonardo-модели — они тратят
        // ~2–3 тыс. Neurons за кадр, при бесплатных 10 тыс./день это невыгодно.
        val excludedIds = setOf(
            "@cf/stabilityai/stable-diffusion-xl-base-1.0",
            "@cf/runwayml/stable-diffusion-v1-5-inpainting",
            "@cf/bytedance/stable-diffusion-xl-lightning",
            "@cf/lykon/dreamshaper-8-lcm",
            "@cf/leonardo/lucid-origin",
            "@cf/leonardo/phoenix-1.0"
        )

        fun byId(id: String): ImageModel? = catalog.firstOrNull { it.id == id }

        fun supportsEditing(id: String): Boolean = id in editingIds
    }
}
