package com.ai.mobileagent.engine

import com.ai.mobileagent.data.AgentDecision
import com.ai.mobileagent.data.ScreenSnapshot

class SafetyGuard {

    private val actionHistory = mutableListOf<String>()

    /**
     * Checks if executing the action is safe under current conditions.
     */
    fun evaluateSafety(decision: AgentDecision, currentSnapshot: ScreenSnapshot): SafetyCheckResult {
        // 1. Password Guard: Protect user credentials
        if (currentSnapshot.containsPassword && decision.action == com.ai.mobileagent.data.ActionType.TYPE) {
            return SafetyCheckResult.Blocked(
                "Blocked action: Screen contains a password field. AI is prevented from typing sensitive credentials."
            )
        }

        // 2. Loop Detection: Prevent runaway loops
        val actionFingerprint = "${decision.action}_${decision.target_id}_${decision.coordinates?.x}_${decision.coordinates?.y}_${currentSnapshot.packageName}"
        actionHistory.add(actionFingerprint)

        if (actionHistory.size >= 4) {
            val lastFour = actionHistory.takeLast(4)
            if (lastFour.all { it == actionFingerprint }) {
                return SafetyCheckResult.Blocked(
                    "Loop detected: The same action was repeated 4 times without progress. Stopping for safety."
                )
            }
        }

        return SafetyCheckResult.Allowed
    }

    fun reset() {
        actionHistory.clear()
    }
}

sealed class SafetyCheckResult {
    object Allowed : SafetyCheckResult()
    data class Blocked(val reason: String) : SafetyCheckResult()
}
