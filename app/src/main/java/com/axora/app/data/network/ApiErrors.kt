package com.axora.app.data.network

// Распознавание ошибок провайдеров, чтобы репозиторий и UI трактовали их одинаково.
object ApiErrors {

    // Cloudflare Workers AI, HTTP 429: 4006 — исчерпана дневная бесплатная квота
    // neurons; 3036/3040 — исторические коды того же класса. Тело ошибки содержит
    // английское сообщение "you have used up your daily free allocation...".
    fun isDailyNeuronLimit(body: String): Boolean =
        body.contains("neurons", ignoreCase = true) ||
            body.contains("[4006") || body.contains("\"4006\"") ||
            body.contains("[3036") || body.contains("\"3036\"") ||
            body.contains("\"3040\"")
}
