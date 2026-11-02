package com.ai.mobileagent.engine

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ai.mobileagent.data.ScreenSnapshot
import com.ai.mobileagent.data.UIElement

object ScreenParser {

    /**
     * Parses the current window hierarchy into a structured list of actionable UI elements.
     */
    fun parseActiveWindow(
        rootNode: AccessibilityNodeInfo?,
        packageName: String,
        activityTitle: String = "",
        screenshotBase64: String? = null
    ): ScreenSnapshot {
        if (rootNode == null) {
            return ScreenSnapshot(
                packageName = packageName,
                activityTitle = activityTitle,
                elements = emptyList(),
                containsPassword = false,
                screenshotBase64 = screenshotBase64
            )
        }

        val collectedElements = mutableListOf<UIElement>()
        var containsPassword = false
        var elementCounter = 1

        val bounds = Rect()

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return

            if (!node.isVisibleToUser) {
                return
            }

            node.getBoundsInScreen(bounds)
            val width = bounds.width()
            val height = bounds.height()

            // Filter out non-visible/zero-size artifacts
            if (width <= 0 || height <= 0) {
                return
            }

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val resId = node.viewIdResourceName?.trim().orEmpty()
            val isClickable = node.isClickable
            val isEditable = node.isEditable
            val isScrollable = node.isScrollable
            val isCheckable = node.isCheckable
            val isChecked = node.isChecked
            val isPassword = node.isPassword

            if (isPassword) {
                containsPassword = true
            }

            // An element is interactive if:
            // 1. It is clickable, editable, checkable, scrollable, or password
            // 2. OR it has descriptive text/content description and is not a plain container
            val hasMeaningfulContent = text.isNotEmpty() || desc.isNotEmpty()
            val isActionable = isClickable || isEditable || isCheckable || isScrollable || isPassword

            if (isActionable || (hasMeaningfulContent && node.childCount == 0)) {
                val element = UIElement(
                    index = elementCounter++,
                    className = node.className?.toString().orEmpty(),
                    text = text,
                    contentDescription = desc,
                    resourceId = resId,
                    bounds = Rect(bounds),
                    isClickable = isClickable,
                    isEditable = isEditable,
                    isScrollable = isScrollable,
                    isCheckable = isCheckable,
                    isChecked = isChecked,
                    isPassword = isPassword
                )
                collectedElements.add(element)
            }

            // Recurse down children
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                traverse(child)
            }
        }

        try {
            traverse(rootNode)
        } catch (e: Exception) {
            // Defensive handling against recycled nodes during hierarchy transitions
        }

        return ScreenSnapshot(
            packageName = packageName,
            activityTitle = activityTitle,
            elements = collectedElements,
            containsPassword = containsPassword,
            screenshotBase64 = screenshotBase64
        )
    }
}
