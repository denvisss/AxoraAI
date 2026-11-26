package com.axora.app.domain.model

// Модели генерации картинок на Cloudflare Workers AI. Порядок в catalog — порядок в селекторе:
// сверху самая мощная, она же по умолчанию.
//
// NB: Cloudflare 20.11.2026 удалил 24 модели (SDXL, Dreamshaper, SDXL-Lightning и др. —
// теперь HTTP 410). В каталог держим только живые модели; новые добавляем по changelog.
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

        val catalog = listOf(
            KLEIN_9B,
            KLEIN_4B,
            FLUX_2_DEV,
            FLUX_1_SCHNELL
        )

        fun byId(id: String): ImageModel? = catalog.firstOrNull { it.id == id }
    }
}
