package com.seongja.jarvis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationRuntimeTest {
    @Test
    fun previewAndSavedStatesReturnToHelix() {
        ImageGenerationRuntime.clear()
        val image = GeneratedImage(
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/png",
            model = "gemini-test",
            prompt = "a test visual",
            text = "",
            elapsedMs = 321L
        )

        ImageGenerationRuntime.storeSuccess("a test visual", image, saved = false)
        val preview = ImageGenerationRuntime.consume()!!
        assertFalse(preview.isError)
        assertFalse(preview.saved)
        assertTrue(preview.message.contains("preview is ready"))

        ImageGenerationRuntime.storeSuccess("a test visual", image, saved = true)
        val saved = ImageGenerationRuntime.consume()!!
        assertTrue(saved.saved)
        assertTrue(saved.message.contains("Pictures/FRIDAY"))
        assertNull(ImageGenerationRuntime.consume())
    }

    @Test
    fun providerFailureIsReturnedTruthfully() {
        ImageGenerationRuntime.clear()
        ImageGenerationRuntime.storeFailure("test", "No Gemini key is configured")
        val failure = ImageGenerationRuntime.consume()!!
        assertTrue(failure.isError)
        assertTrue(failure.message.contains("No Gemini key"))
    }
}
