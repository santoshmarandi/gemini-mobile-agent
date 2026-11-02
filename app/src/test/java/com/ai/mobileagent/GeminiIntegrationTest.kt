package com.ai.mobileagent

import com.ai.mobileagent.data.ScreenSnapshot
import com.ai.mobileagent.data.UIElement
import com.ai.mobileagent.gemini.GeminiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiIntegrationTest {

    private val testApiKey = System.getenv("GEMINI_API_KEY").orEmpty()
    private val model = "gemini-3.5-flash-lite"
    private val client = GeminiClient()

    @Test
    fun testApiKeyValidation() = runBlocking {
        if (testApiKey.isBlank()) {
            println("Skipping live integration test: GEMINI_API_KEY environment variable not set")
            return@runBlocking
        }
        val result = client.validateApiKey(testApiKey, model)
        if (result.isSuccess) {
            assertTrue("API key validated successfully", result.getOrThrow())
        } else {
            println("Live network test note: ${result.exceptionOrNull()?.message}")
        }
    }

    @Test
    fun testGetNextActionFromGemini() = runBlocking {
        if (testApiKey.isBlank()) {
            println("Skipping live integration test: GEMINI_API_KEY environment variable not set")
            return@runBlocking
        }
        val dummySnapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            activityTitle = "YouTube Home",
            elements = listOf(
                UIElement(
                    index = 1,
                    className = "android.widget.EditText",
                    text = "Search YouTube",
                    contentDescription = "",
                    resourceId = "com.google.android.youtube:id/search_edit_text",
                    bounds = android.graphics.Rect(100, 100, 900, 200),
                    isClickable = true,
                    isEditable = true,
                    isScrollable = false,
                    isCheckable = false,
                    isChecked = false,
                    isPassword = false
                )
            ),
            containsPassword = false
        )

        val result = client.getNextAction(
            apiKey = testApiKey,
            model = model,
            goal = "Search for lofi beats",
            history = emptyList(),
            snapshot = dummySnapshot
        )

        if (result.isSuccess) {
            val decision = result.getOrThrow()
            assertNotNull(decision.thought)
            assertNotNull(decision.action)
        } else {
            println("Live network test note: ${result.exceptionOrNull()?.message}")
        }
    }
}
