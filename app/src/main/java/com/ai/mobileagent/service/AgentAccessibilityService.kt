package com.ai.mobileagent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ai.mobileagent.data.ScreenSnapshot
import com.ai.mobileagent.engine.ScreenParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class AgentAccessibilityService : AccessibilityService() {

    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Track active foreground package name
        event?.packageName?.let {
            currentPackage = it.toString()
        }
    }

    override fun onInterrupt() {
        _isServiceActive.value = false
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceActive.value = false
        instance = null
    }

    /**
     * Captures current active screen hierarchy snapshot.
     */
    fun captureCurrentScreen(withScreenshot: Boolean = false): ScreenSnapshot {
        val root = rootInActiveWindow
        val pkg = currentPackage.ifBlank { root?.packageName?.toString().orEmpty() }

        var base64Screenshot: String? = null
        if (withScreenshot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Synchronous block or quick wait for screenshot
        }

        return ScreenParser.parseActiveWindow(
            rootNode = root,
            packageName = pkg,
            activityTitle = "",
            screenshotBase64 = base64Screenshot
        )
    }

    /**
     * Dispatch tap at specific screen coordinates.
     */
    fun tapCoordinates(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatch swipe / scroll gesture between two points.
     */
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Sets text on an editable node.
     */
    fun setTextOnNode(targetIndex: Int, text: String): Boolean {
        val snapshot = captureCurrentScreen(false)
        val targetElement = snapshot.elements.find { it.index == targetIndex } ?: return false

        // Tap first to ensure focus
        tapCoordinates(targetElement.centerX, targetElement.centerY)

        val root = rootInActiveWindow ?: return false
        val targetNode = findNodeMatchingBounds(root, targetElement.bounds.centerX(), targetElement.bounds.centerY())
            ?: return false

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Clicks an element by index using either direct Accessibility action or coordinate tap.
     */
    fun clickElement(targetIndex: Int): Boolean {
        val snapshot = captureCurrentScreen(false)
        val targetElement = snapshot.elements.find { it.index == targetIndex } ?: return false

        val root = rootInActiveWindow
        val targetNode = if (root != null) {
            findNodeMatchingBounds(root, targetElement.bounds.centerX(), targetElement.bounds.centerY())
        } else null

        // Try direct accessibility click first if supported
        val directClicked = targetNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true

        // Fall back to coordinate tap if direct node click is unsupported or fails
        return if (!directClicked) {
            tapCoordinates(targetElement.centerX, targetElement.centerY)
        } else {
            true
        }
    }

    /**
     * Captures a screenshot via API 30+ AccessibilityService.
     */
    suspend fun captureScreenshotBase64(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val deferred = CompletableDeferred<String?>()
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshotResult.hardwareBuffer,
                                screenshotResult.colorSpace
                            )
                            if (bitmap != null) {
                                val out = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                                deferred.complete(base64)
                            } else {
                                deferred.complete(null)
                            }
                        } catch (e: Exception) {
                            deferred.complete(null)
                        } finally {
                            screenshotResult.hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        deferred.complete(null)
                    }
                }
            )
        } catch (e: Exception) {
            deferred.complete(null)
        }
        return deferred.await()
    }

    private fun findNodeMatchingBounds(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) {
            return null
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeMatchingBounds(child, x, y)
            if (found != null) {
                return found
            }
        }
        return node
    }

    companion object {
        var instance: AgentAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()

        var currentPackage: String = ""
            private set
    }
}
