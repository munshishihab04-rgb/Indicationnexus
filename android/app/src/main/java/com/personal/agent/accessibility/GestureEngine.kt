package com.personal.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "GestureEngine"

/**
 * Dispatches touch gestures via [AccessibilityService.dispatchGesture].
 * Used as fallback when node-based actions are unavailable.
 */
class GestureEngine(private val service: AccessibilityService) {

    /**
     * Taps at the center of the given node's bounds.
     */
    suspend fun tap(node: UiNodeSnapshot): Boolean =
        tapAt(node.centerX.toFloat(), node.centerY.toFloat())

    /**
     * Taps at the given screen coordinates.
     */
    suspend fun tapAt(x: Float, y: Float, durationMs: Long = 50): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    /**
     * Performs a long tap at the given coordinates.
     */
    suspend fun longTapAt(x: Float, y: Float, durationMs: Long = 800): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    /**
     * Swipes from (startX, startY) to (endX, endY).
     */
    suspend fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    /**
     * Swipes within a scrollable node (up/down/left/right).
     */
    suspend fun swipeNode(node: UiNodeSnapshot, direction: String): Boolean {
        val cx = node.centerX.toFloat()
        val cy = node.centerY.toFloat()
        val dx = (node.width * 0.4).toFloat()
        val dy = (node.height * 0.4).toFloat()
        return when (direction.lowercase()) {
            "up"    -> swipe(cx, cy + dy, cx, cy - dy)
            "down"  -> swipe(cx, cy - dy, cx, cy + dy)
            "left"  -> swipe(cx + dx, cy, cx - dx, cy)
            "right" -> swipe(cx - dx, cy, cx + dx, cy)
            else    -> { Log.w(TAG, "Unknown direction: $direction"); false }
        }
    }

    private suspend fun dispatchGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(g: GestureDescription) {
                    Log.w(TAG, "Gesture cancelled")
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
        }
}
