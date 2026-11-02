package com.ai.mobileagent

import com.ai.mobileagent.data.ActionType
import com.ai.mobileagent.data.AgentDecision
import com.ai.mobileagent.data.Coordinates
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun testDecisionJsonDeserialization() {
        val sampleJson = """
            {
              "thought": "I need to click on the search bar to enter the query",
              "action": "CLICK",
              "target_id": 2,
              "coordinates": {"x": 540.0, "y": 120.0},
              "text": null,
              "package_name": null,
              "is_finished": false,
              "message": null
            }
        """.trimIndent()

        val decision = json.decodeFromString(AgentDecision.serializer(), sampleJson)

        assertEquals("I need to click on the search bar to enter the query", decision.thought)
        assertEquals(ActionType.CLICK, decision.action)
        assertEquals(2, decision.target_id)
        assertNotNull(decision.coordinates)
        assertEquals(540.0f, decision.coordinates?.x)
        assertEquals(120.0f, decision.coordinates?.y)
        assertEquals(false, decision.is_finished)
    }

    @Test
    fun testOpenAppDecisionDeserialization() {
        val sampleJson = """
            {
              "thought": "Opening YouTube app",
              "action": "OPEN_APP",
              "package_name": "com.google.android.youtube",
              "is_finished": false
            }
        """.trimIndent()

        val decision = json.decodeFromString(AgentDecision.serializer(), sampleJson)

        assertEquals(ActionType.OPEN_APP, decision.action)
        assertEquals("com.google.android.youtube", decision.package_name)
    }

    @Test
    fun testFinishDecisionDeserialization() {
        val sampleJson = """
            {
              "thought": "Video has started playing",
              "action": "FINISH",
              "is_finished": true,
              "message": "Playing lofi hip hop radio"
            }
        """.trimIndent()

        val decision = json.decodeFromString(AgentDecision.serializer(), sampleJson)

        assertEquals(ActionType.FINISH, decision.action)
        assertTrue(decision.is_finished)
        assertEquals("Playing lofi hip hop radio", decision.message)
    }
}
