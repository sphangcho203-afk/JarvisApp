package com.seongja.jarvis

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCommandIntentTest {
    @Test
    fun threeDimensionalModelRoutesToVisualLab() {
        val prompt = ImageCommandIntent.promptFor(
            "FRIDAY, show me a 3D model image of the Iron Man suit"
        )
        assertNotNull(prompt)
        assertTrue(prompt!!.contains("Iron Man", ignoreCase = true))
    }

    @Test
    fun visualizeRoutesToVisualLab() {
        assertNotNull(
            ImageCommandIntent.promptFor(
                "Visualize a futuristic phone assistant command centre"
            )
        )
    }

    @Test
    fun ordinaryQuestionDoesNotOpenVisualLab() {
        assertNull(ImageCommandIntent.promptFor("What is a 3D model?"))
    }
}
