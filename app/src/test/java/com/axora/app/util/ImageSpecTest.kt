package com.axora.app.util

import com.axora.app.domain.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSpecTest {

    @Test
    fun generationSizesCoverAllFormats() {
        assertEquals(1024 to 1024, ImageSpec.generationSize(ImageFormat.SQUARE))
        assertEquals(896 to 1152, ImageSpec.generationSize(ImageFormat.PORTRAIT))
        assertEquals(1152 to 896, ImageSpec.generationSize(ImageFormat.LANDSCAPE))
    }

    @Test
    fun editOutputDownscalesLargeSource() {
        assertEquals(1024 to 512, ImageSpec.editOutputSize(2000, 1000))
    }

    @Test
    fun editOutputKeepsSmallSourceRoundedTo32() {
        assertEquals(800 to 608, ImageSpec.editOutputSize(800, 608))
    }

    @Test
    fun editOutputClampsSmallSidesToMinimum() {
        assertEquals(256 to 256, ImageSpec.editOutputSize(10, 10))
    }

    @Test
    fun editOutputFallsBackForUnknownDimensions() {
        assertEquals(1024 to 1024, ImageSpec.editOutputSize(0, 100))
        assertEquals(1024 to 1024, ImageSpec.editOutputSize(100, -1))
    }

    @Test
    fun imageExtensionDetectsMagicBytes() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(8)
        val webp = ByteArray(8) + byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(), 0)
        assertEquals("png", ImageSpec.imageExtension(png))
        assertEquals("webp", ImageSpec.imageExtension(webp))
        assertEquals("jpg", ImageSpec.imageExtension(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(10)))
        assertEquals("jpg", ImageSpec.imageExtension(ByteArray(0)))
    }
}
