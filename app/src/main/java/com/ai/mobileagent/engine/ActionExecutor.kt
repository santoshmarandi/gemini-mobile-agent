package com.ai.mobileagent.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import com.ai.mobileagent.data.ActionType
import com.ai.mobileagent.data.AgentDecision
import com.ai.mobileagent.service.AgentAccessibilityService
import kotlinx.coroutines.delay

class ActionExecutor(private val context: Context) {

    suspend fun execute(decision: AgentDecision): ExecutionResult {
        val service = AgentAccessibilityService.instance
            ?: return ExecutionResult(false, "Accessibility Service is not enabled or connected")

        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        return try {
            when (decision.action) {
                ActionType.CLICK -> {
                    if (decision.target_id != null) {
                        val success = service.clickElement(decision.target_id)
                        ExecutionResult(success, "Clicked element [${decision.target_id}]")
                    } else if (decision.coordinates != null) {
                        val success = service.tapCoordinates(decision.coordinates.x, decision.coordinates.y)
                        ExecutionResult(success, "Tapped coordinates (${decision.coordinates.x}, ${decision.coordinates.y})")
                    } else {
                        ExecutionResult(false, "Click action missing target_id or coordinates")
                    }
                }

                ActionType.TYPE -> {
                    val textToType = decision.text.orEmpty()
                    if (decision.target_id != null) {
                        val success = service.setTextOnNode(decision.target_id, textToType)
                        ExecutionResult(success, "Typed \"$textToType\" into element [${decision.target_id}]")
                    } else {
                        ExecutionResult(false, "Type action missing target_id")
                    }
                }

                ActionType.SCROLL_DOWN -> {
                    val startX = screenWidth / 2f
                    val startY = screenHeight * 0.75f
                    val endY = screenHeight * 0.25f
                    val success = service.swipe(startX, startY, startX, endY, 400)
                    ExecutionResult(success, "Scrolled down")
                }

                ActionType.SCROLL_UP -> {
                    val startX = screenWidth / 2f
                    val startY = screenHeight * 0.25f
                    val endY = screenHeight * 0.75f
                    val success = service.swipe(startX, startY, startX, endY, 400)
                    ExecutionResult(success, "Scrolled up")
                }

                ActionType.PRESS_BACK -> {
                    val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    ExecutionResult(success, "Pressed Back")
                }

                ActionType.PRESS_HOME -> {
                    val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    ExecutionResult(success, "Pressed Home")
                }

                ActionType.PRESS_RECENTS -> {
                    val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                    ExecutionResult(success, "Opened App Switcher")
                }

                ActionType.OPEN_APP -> {
                    val pkg = decision.package_name.orEmpty()
                    if (pkg.isBlank()) {
                        ExecutionResult(false, "OPEN_APP action missing package_name")
                    } else {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            ExecutionResult(true, "Launched app: $pkg")
                        } else {
                            ExecutionResult(false, "App package '$pkg' is not installed on this device")
                        }
                    }
                }

                ActionType.WAIT -> {
                    delay(1500)
                    ExecutionResult(true, "Waited for UI state update")
                }

                ActionType.FINISH -> {
                    ExecutionResult(true, decision.message ?: "Task finished successfully")
                }

                ActionType.FAIL -> {
                    ExecutionResult(false, decision.message ?: "Agent failed to complete task")
                }
            }
        } catch (e: Exception) {
            ExecutionResult(false, "Action execution error: ${e.localizedMessage}")
        }
    }
}

data class ExecutionResult(
    val isSuccess: Boolean,
    val message: String
)
