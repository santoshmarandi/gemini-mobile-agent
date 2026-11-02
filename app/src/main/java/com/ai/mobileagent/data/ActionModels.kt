package com.ai.mobileagent.data

import android.graphics.Rect
import kotlinx.serialization.Serializable

/**
 * Representation of an interactive UI element found on screen.
 */
data class UIElement(
    val index: Int,
    val className: String,
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isPassword: Boolean
) {
    val centerX: Float get() = bounds.centerX().toFloat()
    val centerY: Float get() = bounds.centerY().toFloat()

    fun toPromptString(): String {
        val label = when {
            text.isNotBlank() && contentDescription.isNotBlank() -> "\"$text\" ($contentDescription)"
            text.isNotBlank() -> "\"$text\""
            contentDescription.isNotBlank() -> "\"$contentDescription\""
            resourceId.isNotBlank() -> "id:${resourceId.substringAfter(":id/")}"
            else -> className.substringAfterLast('.')
        }
        val type = when {
            isEditable -> "EditText"
            isPassword -> "PasswordField"
            isCheckable -> "Checkbox[checked=$isChecked]"
            isScrollable -> "Scrollable"
            isClickable -> "Button/Clickable"
            else -> "View"
        }
        return "[$index] $type: $label | bounds: [${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom}]"
    }
}

/**
 * Snapshot of the current screen observed by the agent.
 */
data class ScreenSnapshot(
    val packageName: String,
    val activityTitle: String,
    val elements: List<UIElement>,
    val containsPassword: Boolean,
    val screenshotBase64: String? = null
) {
    fun toGeminiContext(): String {
        val builder = StringBuilder()
        builder.appendLine("Current Foreground App: $packageName")
        if (activityTitle.isNotBlank()) {
            builder.appendLine("Window Title: $activityTitle")
        }
        builder.appendLine("Screen Elements (${elements.size} detected):")
        if (elements.isEmpty()) {
            builder.appendLine("(No interactive elements found on screen)")
        } else {
            elements.forEach { element ->
                builder.appendLine(element.toPromptString())
            }
        }
        return builder.toString()
    }
}

/**
 * Available action types the Agent can perform.
 */
@Serializable
enum class ActionType {
    CLICK,
    TYPE,
    SCROLL_DOWN,
    SCROLL_UP,
    PRESS_BACK,
    PRESS_HOME,
    PRESS_RECENTS,
    OPEN_APP,
    WAIT,
    FINISH,
    FAIL
}

@Serializable
data class Coordinates(
    val x: Float,
    val y: Float
)

/**
 * Structured decision returned by Gemini.
 */
@Serializable
data class AgentDecision(
    val thought: String,
    val action: ActionType,
    val target_id: Int? = null,
    val coordinates: Coordinates? = null,
    val text: String? = null,
    val package_name: String? = null,
    val is_finished: Boolean = false,
    val message: String? = null
)

/**
 * Real-time log message for UI feedback.
 */
data class AgentLog(
    val step: Int,
    val thought: String,
    val actionDescription: String,
    val timestamp: Long = System.currentTimeMillis()
)
