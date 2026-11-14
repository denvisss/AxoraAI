package com.axora.app.util

import com.axora.app.domain.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSpecTest {

    @Test
    fun freeModelSupportsAllFormatsAt768() {
        assertEquals(768 to 768, ImageSpec.generationSize(ImageFormat.SQUARE, "free", keyed = false))
        assertEquals(768 to 1024, ImageSpec.generationSize(ImageFormat.PORTRAIT, "free", keyed = false))
        assertEquals(1024 to 768, ImageSpec.generationSize(ImageFormat.LANDSCAPE, "free", keyed = false))
    }

    @Test
    fun keyedKleinUses1536Base() {
        val model = "black-forest-labs/flux.2-klein-4b"
        assertEquals(1536 to 1536, ImageSpec.generationSize(ImageFormat.SQUARE, model, keyed = true))
        assertEquals(1152 to 1536, ImageSpec.generationSize(ImageFormat.PORTRAIT, model, keyed = true))
        assertEquals(1536 to 1152, ImageSpec.generationSize(ImageFormat.LANDSCAPE, model, keyed = true))
    }

    @Test
    fun keyedKontextUses1024Base() {
        val model = "flux-kontext"
        assertEquals(1024 to 1024, ImageSpec.generationSize(ImageFormat.SQUARE, model, keyed = true))
        assertEquals(768 to 1024, ImageSpec.generationSize(ImageFormat.PORTRAIT, model, keyed = true))
        assertEquals(1024 to 768, ImageSpec.generationSize(ImageFormat.LANDSCAPE, model, keyed = true))
    }

    @Test
    fun editSizeFallsBackToAutoForUnknownDimensions() {
        assertEquals("auto", ImageSpec.editSize(0, 100, "klein"))
        assertEquals("auto", ImageSpec.editSize(100, -1, "klein"))
    }

    @Test
    fun editSizeDownscalesLargeSourceToModelLimit() {
        assertEquals("1536x768", ImageSpec.editSize(2000, 1000, "klein"))
        assertEquals("1024x512", ImageSpec.editSize(2000, 1000, "kontext"))
    }

    @Test
    fun editSizeRoundsTo32AndKeepsSmallSources() {
        assertEquals("800x608", ImageSpec.editSize(800, 608, "klein"))
        assertEquals("32x32", ImageSpec.editSize(10, 10, "klein"))
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