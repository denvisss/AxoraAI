package com.axora.app.util

import com.axora.app.domain.model.ImageFormat

object ImageSpec {

    fun generationSize(format: ImageFormat, model: String, keyed: Boolean): Pair<Int, Int> {
        if (!keyed) {
            return when (format) {
                ImageFormat.SQUARE -> 768 to 768
                ImageFormat.PORTRAIT -> 768 to 1024
                ImageFormat.LANDSCAPE -> 1024 to 768
            }
        }
        val base = if (model.contains("kontext")) 1024 else 1536
        val short = base * 3 / 4
        return when (format) {
            ImageFormat.SQUARE -> base to base
            ImageFormat.PORTRAIT -> short to base
            ImageFormat.LANDSCAPE -> base to short
        }
    }

    fun editSize(width: Int, height: Int, model: String): String {
        if (width <= 0 || height <= 0) return "auto"
        val maxSide = if (model.contains("kontext")) 1024 else 1536
        val scale = minOf(1f, maxSide.toFloat() / maxOf(width, height))
        val w = ((width * scale).toInt() / 32 * 32).coerceAtLeast(32)
        val h = ((height * scale).toInt() / 32 * 32).coerceAtLeast(32)
        return "${w}x$h"
    }

    fun imageExtension(bytes: ByteArray): String = when {
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size > 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "webp"
        else -> "jpg"
    }
}