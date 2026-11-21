package com.axora.app.util

import com.axora.app.domain.model.ImageFormat

object ImageSpec {

    const val MIN_SIDE = 256
    const val MAX_SIDE = 1920
    const val EDIT_OUTPUT_MAX = 1024
    const val EDIT_INPUT_MAX = 500

    fun generationSize(format: ImageFormat): Pair<Int, Int> = when (format) {
        ImageFormat.SQUARE -> 1024 to 1024
        ImageFormat.PORTRAIT -> 896 to 1152
        ImageFormat.LANDSCAPE -> 1152 to 896
        ImageFormat.WIDE -> 1536 to 864
    }

    fun editOutputSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return EDIT_OUTPUT_MAX to EDIT_OUTPUT_MAX
        val scale = minOf(1f, EDIT_OUTPUT_MAX.toFloat() / maxOf(width, height))
        return clampSide(width * scale) to clampSide(height * scale)
    }

    fun imageExtension(bytes: ByteArray): String = when {
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size > 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "webp"
        else -> "jpg"
    }

    private fun clampSide(side: Float): Int = ((side / 32f).toInt() * 32).coerceIn(MIN_SIDE, MAX_SIDE)
}
