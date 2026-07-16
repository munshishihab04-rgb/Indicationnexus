package com.personal.agent.accessibility

import android.graphics.Rect
import com.squareup.moshi.JsonClass

/**
 * Immutable snapshot of a single UI node from the Accessibility tree.
 * Captured once and passed around safely without holding live node references.
 */
@JsonClass(generateAdapter = true)
data class UiNodeSnapshot(
    val id: String?,                    // viewId resource name (e.g. "com.app:id/btn_ok")
    val text: String?,                  // node text content
    val contentDescription: String?,   // accessibility description
    val viewId: String?,               // resource-id (short form, e.g. "btn_ok")
    val className: String?,            // e.g. "android.widget.Button"
    val packageName: String?,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val clickable: Boolean,
    val longClickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val checked: Boolean,
    val selected: Boolean,
    val focusable: Boolean,
    val depth: Int,
    val childCount: Int,
    val children: List<UiNodeSnapshot> = emptyList()
) {
    val centerX: Int get() = (boundsLeft + boundsRight) / 2
    val centerY: Int get() = (boundsTop + boundsBottom) / 2
    val width: Int  get() = boundsRight - boundsLeft
    val height: Int get() = boundsBottom - boundsTop
    val bounds: Rect get() = Rect(boundsLeft, boundsTop, boundsRight, boundsBottom)

    /** Returns a compact label for logging. */
    fun label(): String = buildString {
        text?.let { append("\"${it.take(30)}\"") }
        contentDescription?.let { if (text == null) append("desc:\"${it.take(30)}\"") }
        viewId?.let { append(" [$it]") }
        className?.substringAfterLast(".")?.let { append(" ($it)") }
    }.trim()
}

/**
 * Result of an accessibility action attempt.
 */
data class ActionResult(
    val success: Boolean,
    val message: String = "",
    val errorCode: String? = null
) {
    companion object {
        fun ok(msg: String = "") = ActionResult(true, msg)
        fun fail(code: String, msg: String = "") = ActionResult(false, msg, code)
    }
}

/**
 * Supported action types (mirrors doc 05).
 */
enum class ActionType {
    CLICK,
    LONG_CLICK,
    TYPE,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    SWIPE,
    COPY,
    PASTE,
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    WAIT_ELEMENT,
    WAIT_TEXT,
    SCREENSHOT,
    FOCUS,
    CLEAR_TEXT,
}

/**
 * A single action command passed to [ActionExecutor].
 */
data class ActionCommand(
    val type: ActionType,
    val targetText: String? = null,
    val targetViewId: String? = null,
    val targetClass: String? = null,
    val targetDescription: String? = null,
    val text: String? = null,                // text to type
    val timeoutMs: Long = 10_000,
    val scrollDirection: String = "down",    // for swipe/scroll
    val swipeStartX: Float = 0f,
    val swipeStartY: Float = 0f,
    val swipeEndX: Float = 0f,
    val swipeEndY: Float = 0f,
    val swipeDurationMs: Long = 300,
    val useFallbackGesture: Boolean = false
)
