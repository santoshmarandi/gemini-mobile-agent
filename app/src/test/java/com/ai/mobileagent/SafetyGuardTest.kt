package com.ai.mobileagent

import android.graphics.Rect
import com.ai.mobileagent.data.ActionType
import com.ai.mobileagent.data.AgentDecision
import com.ai.mobileagent.data.ScreenSnapshot
import com.ai.mobileagent.data.UIElement
import com.ai.mobileagent.engine.SafetyCheckResult
import com.ai.mobileagent.engine.SafetyGuard
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGuardTest {

    @Test
    fun testBlockPasswordTyping() {
        val guard = SafetyGuard()
        val snapshotWithPassword = ScreenSnapshot(
            packageName = "com.android.settings",
            activityTitle = "Security Settings",
            elements = emptyList(),
            containsPassword = true
        )

        val typingDecision = AgentDecision(
            thought = "Attempting to type password",
            action = ActionType.TYPE,
            target_id = 1,
            text = "secret123"
        )

        val result = guard.evaluateSafety(typingDecision, snapshotWithPassword)
        assertTrue(result is SafetyCheckResult.Blocked)
    }

    @Test
    fun testLoopDetection() {
        val guard = SafetyGuard()
        val snapshot = ScreenSnapshot(
            packageName = "com.test.app",
            activityTitle = "Main",
            elements = emptyList(),
            containsPassword = false
        )

        val repeatedAction = AgentDecision(
            thought = "Clicking button",
            action = ActionType.CLICK,
            target_id = 5
        )

        // 1st, 2nd, 3rd action allowed
        assertTrue(guard.evaluateSafety(repeatedAction, snapshot) is SafetyCheckResult.Allowed)
        assertTrue(guard.evaluateSafety(repeatedAction, snapshot) is SafetyCheckResult.Allowed)
        assertTrue(guard.evaluateSafety(repeatedAction, snapshot) is SafetyCheckResult.Allowed)

        // 4th repeated action is blocked by loop detection
        val result = guard.evaluateSafety(repeatedAction, snapshot)
        assertTrue(result is SafetyCheckResult.Blocked)
    }
}
