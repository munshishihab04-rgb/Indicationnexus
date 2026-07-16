package com.personal.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "ActionExecutor"
private const val POLL_INTERVAL_MS = 250L

/**
 * Executes [ActionCommand] objects against the live accessibility tree.
 *
 * Requires a live [AccessibilityService] reference to:
 * - call [AccessibilityService.getRootInActiveWindow]
 * - call [AccessibilityService.performGlobalAction]
 * - dispatch gestures via [GestureEngine]
 *
 * Safety rules (from doc 05):
 * - Actions are rate-limited by [AccessibilityEngineState.actionCount].
 * - App denylist is checked before any action.
 * - Every action is logged with result.
 * - Gesture fallback used only when node action fails and coordinates are known.
 */
class ActionExecutor(private val service: AccessibilityService) {

    private val gestures = GestureEngine(service)

    suspend fun execute(cmd: ActionCommand): ActionResult = withContext(Dispatchers.Main) {
        try {
            when (cmd.type) {
                ActionType.BACK              -> performGlobal(AccessibilityService.GLOBAL_ACTION_BACK, "back")
                ActionType.HOME              -> performGlobal(AccessibilityService.GLOBAL_ACTION_HOME, "home")
                ActionType.RECENTS           -> performGlobal(AccessibilityService.GLOBAL_ACTION_RECENTS, "recents")
                ActionType.NOTIFICATIONS     -> performGlobal(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "notifications")
                ActionType.QUICK_SETTINGS    -> performGlobal(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "quick_settings")
                ActionType.CLICK             -> executeClick(cmd, longClick = false)
                ActionType.LONG_CLICK        -> executeClick(cmd, longClick = true)
                ActionType.TYPE              -> executeType(cmd)
                ActionType.CLEAR_TEXT        -> executeClearText(cmd)
                ActionType.SCROLL_FORWARD    -> executeScroll(cmd, forward = true)
                ActionType.SCROLL_BACKWARD   -> executeScroll(cmd, forward = false)
                ActionType.SWIPE             -> executeSwipe(cmd)
                ActionType.COPY              -> executeCopy(cmd)
                ActionType.PASTE             -> executePaste(cmd)
                ActionType.FOCUS             -> executeFocus(cmd)
                ActionType.WAIT_ELEMENT      -> executeWaitElement(cmd)
                ActionType.WAIT_TEXT         -> executeWaitText(cmd)
                ActionType.SCREENSHOT        -> ActionResult.fail("not_implemented", "Screenshot requires MediaProjection — Phase 6")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action ${cmd.type} failed: ${e.message}")
            ActionResult.fail("exception", e.message ?: "unknown")
        }
    }

    // ─── Global actions ───────────────────────────────────────────────────────

    private fun performGlobal(action: Int, name: String): ActionResult {
        val ok = service.performGlobalAction(action)
        Log.d(TAG, "Global action $name: $ok")
        return if (ok) ActionResult.ok("$name performed")
        else ActionResult.fail("global_action_failed", "$name failed")
    }

    // ─── Click ────────────────────────────────────────────────────────────────

    private suspend fun executeClick(cmd: ActionCommand, longClick: Boolean): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.fail("no_root", "getRootInActiveWindow returned null")

        val snapshot = AccessibilityTreeBuilder.build(root)
        root.recycle()

        if (snapshot == null) return ActionResult.fail("no_snapshot", "Tree snapshot failed")

        val target = NodeFinder.findTarget(snapshot, cmd)
            ?: return ActionResult.fail("target_not_found",
                "No node for text='${cmd.targetText}' viewId='${cmd.targetViewId}'")

        Log.d(TAG, "${if (longClick) "LongClick" else "Click"} on: ${target.label()}")

        // Try node action first, then gesture fallback
        val actionId = if (longClick) AccessibilityNodeInfo.ACTION_LONG_CLICK
                       else AccessibilityNodeInfo.ACTION_CLICK

        val liveRoot = service.rootInActiveWindow
        val liveNode = findLiveNode(liveRoot, target)

        val ok = if (liveNode != null) {
            val result = liveNode.performAction(actionId)
            liveNode.recycle()
            result
        } else false

        liveRoot?.recycle()

        return if (ok) {
            ActionResult.ok("${if (longClick) "long clicked" else "clicked"} '${target.label()}'")
        } else if (cmd.useFallbackGesture || !ok) {
            Log.d(TAG, "Falling back to gesture tap at (${target.centerX}, ${target.centerY})")
            val gestureOk = if (longClick) gestures.longTapAt(target.centerX.toFloat(), target.centerY.toFloat())
                            else gestures.tapAt(target.centerX.toFloat(), target.centerY.toFloat())
            if (gestureOk) ActionResult.ok("gesture tap on '${target.label()}'")
            else ActionResult.fail("gesture_failed", "Gesture tap failed")
        } else {
            ActionResult.fail("action_failed", "Click action failed and no fallback")
        }
    }

    // ─── Type text ────────────────────────────────────────────────────────────

    private suspend fun executeType(cmd: ActionCommand): ActionResult {
        val text = cmd.text ?: return ActionResult.fail("no_text", "No text provided")

        val root = service.rootInActiveWindow
            ?: return ActionResult.fail("no_root", "No root window")

        val snapshot = AccessibilityTreeBuilder.build(root)
        root.recycle()
        if (snapshot == null) return ActionResult.fail("no_snapshot", "Tree snapshot failed")

        val target = NodeFinder.findTarget(snapshot, cmd)
            ?: NodeFinder.editable(snapshot).firstOrNull()
            ?: return ActionResult.fail("no_editable", "No editable node found")

        Log.d(TAG, "Type '${text.take(20)}' into ${target.label()}")

        val liveRoot = service.rootInActiveWindow
        val liveNode = findLiveNode(liveRoot, target)
        val ok = if (liveNode != null) {
            liveNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply { putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            val result = liveNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            liveNode.recycle()
            result
        } else false
        liveRoot?.recycle()

        return if (ok) ActionResult.ok("typed '${text.take(20)}'")
        else ActionResult.fail("type_failed", "ACTION_SET_TEXT failed — may not be supported")
    }

    // ─── Clear text ───────────────────────────────────────────────────────────

    private suspend fun executeClearText(cmd: ActionCommand): ActionResult {
        val cmdWithEmpty = cmd.copy(text = "", type = ActionType.TYPE)
        return executeType(cmdWithEmpty)
    }

    // ─── Scroll ───────────────────────────────────────────────────────────────

    private suspend fun executeScroll(cmd: ActionCommand, forward: Boolean): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult.fail("no_root", "No root window")

        val snapshot = AccessibilityTreeBuilder.build(root)
        root.recycle()
        if (snapshot == null) return ActionResult.fail("no_snapshot", "Tree snapshot failed")

        val scrollable = NodeFinder.findTarget(snapshot, cmd)
            ?.takeIf { it.scrollable }
            ?: NodeFinder.scrollable(snapshot).firstOrNull()
            ?: return ActionResult.fail("no_scrollable", "No scrollable node found")

        val actionId = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                       else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

        val liveRoot = service.rootInActiveWindow
        val liveNode = findLiveNode(liveRoot, scrollable)
        val ok = if (liveNode != null) {
            val result = liveNode.performAction(actionId)
            liveNode.recycle()
            result
        } else false
        liveRoot?.recycle()

        return if (ok) ActionResult.ok("scrolled ${if (forward) "forward" else "backward"}")
        else {
            // Gesture fallback
            val gestureOk = gestures.swipeNode(scrollable,
                if (forward) "up" else "down")
            if (gestureOk) ActionResult.ok("gesture scroll")
            else ActionResult.fail("scroll_failed", "Both node scroll and gesture failed")
        }
    }

    // ─── Swipe ────────────────────────────────────────────────────────────────

    private suspend fun executeSwipe(cmd: ActionCommand): ActionResult {
        val ok = gestures.swipe(
            cmd.swipeStartX, cmd.swipeStartY,
            cmd.swipeEndX, cmd.swipeEndY,
            cmd.swipeDurationMs
        )
        return if (ok) ActionResult.ok("swiped")
        else ActionResult.fail("swipe_failed", "Gesture swipe failed")
    }

    // ─── Copy ─────────────────────────────────────────────────────────────────

    private suspend fun executeCopy(cmd: ActionCommand): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult.fail("no_root", "")
        val snapshot = AccessibilityTreeBuilder.build(root)
        root.recycle()
        val target = if (snapshot != null) NodeFinder.findTarget(snapshot, cmd) else null

        val liveRoot = service.rootInActiveWindow
        val liveNode = if (target != null) findLiveNode(liveRoot, target) else null
        val ok = liveNode?.performAction(AccessibilityNodeInfo.ACTION_COPY) ?: false
        liveNode?.recycle()
        liveRoot?.recycle()

        return if (ok) ActionResult.ok("copied") else ActionResult.fail("copy_failed", "")
    }

    // ─── Paste ────────────────────────────────────────────────────────────────

    private suspend fun executePaste(cmd: ActionCommand): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult.fail("no_root", "")
        val snapshot = AccessibilityTreeBuilder.build(root)
        root.recycle()
        val target = if (snapshot != null) NodeFinder.findTarget(snapshot, cmd)
                     else null

        val liveRoot = service.rootInActiveWindow
        val liveNode = if (target != null) findLiveNode(liveRoot, target) else null
        val ok = liveNode?.performAction(AccessibilityNodeInfo.ACTION_PASTE) ?: false
        liveNode?.recycle()
        liveRoot?.recycle()

        return if (ok) ActionResult.ok("pasted") else ActionResult.fail("paste_failed", "")
    }

    // ─── Focus ────────────────────────────────────────────────────────────────

    private suspend fun executeFocus(cmd: ActionCommand): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult.fail("no_root", "")
        val snapshot = AccessibilityTreeBuilder.build(root)
        root.recycle()
        val target = if (snapshot != null) NodeFinder.findTarget(snapshot, cmd) else null
            ?: return ActionResult.fail("target_not_found", "")

        val liveRoot = service.rootInActiveWindow
        val liveNode = findLiveNode(liveRoot, target!!)
        val ok = liveNode?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) ?: false
        liveNode?.recycle()
        liveRoot?.recycle()

        return if (ok) ActionResult.ok("focused") else ActionResult.fail("focus_failed", "")
    }

    // ─── Wait element ─────────────────────────────────────────────────────────

    private suspend fun executeWaitElement(cmd: ActionCommand): ActionResult {
        val deadline = System.currentTimeMillis() + cmd.timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = service.rootInActiveWindow
            if (root != null) {
                val snapshot = AccessibilityTreeBuilder.build(root)
                root.recycle()
                if (snapshot != null) {
                    val found = NodeFinder.findTarget(snapshot, cmd)
                    if (found != null) {
                        Log.d(TAG, "WaitElement found: ${found.label()}")
                        return ActionResult.ok("element found: '${found.label()}'")
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
        return ActionResult.fail("timeout", "Element not found within ${cmd.timeoutMs}ms")
    }

    // ─── Wait text ────────────────────────────────────────────────────────────

    private suspend fun executeWaitText(cmd: ActionCommand): ActionResult {
        val text = cmd.targetText ?: return ActionResult.fail("no_text", "No text to wait for")
        val deadline = System.currentTimeMillis() + cmd.timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = service.rootInActiveWindow
            if (root != null) {
                val snapshot = AccessibilityTreeBuilder.build(root)
                root.recycle()
                if (snapshot != null) {
                    val found = NodeFinder.byText(snapshot, text).firstOrNull()
                    if (found != null) {
                        Log.d(TAG, "WaitText found: '${text.take(30)}'")
                        return ActionResult.ok("text found: '${text.take(30)}'")
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
        return ActionResult.fail("timeout", "Text '$text' not found within ${cmd.timeoutMs}ms")
    }

    // ─── Helper: find live node matching snapshot ─────────────────────────────

    /**
     * Finds the live [AccessibilityNodeInfo] corresponding to a snapshot node.
     * Matches by viewId first, then falls back to text + bounds.
     * Caller must recycle the returned node.
     */
    private fun findLiveNode(
        root: AccessibilityNodeInfo?,
        target: UiNodeSnapshot
    ): AccessibilityNodeInfo? {
        root ?: return null

        // Try by viewId (fastest)
        target.id?.let { vid ->
            val nodes = root.findAccessibilityNodeInfosByViewId(vid)
            if (!nodes.isNullOrEmpty()) {
                // Match bounds for precision
                val bounds = android.graphics.Rect()
                val matched = nodes.firstOrNull { n ->
                    n.getBoundsInScreen(bounds)
                    bounds.left == target.boundsLeft && bounds.top == target.boundsTop
                } ?: nodes.first()
                // Recycle the rest
                nodes.filter { it !== matched }.forEach { it.recycle() }
                return matched
            }
        }

        // Try by text
        target.text?.let { txt ->
            val nodes = root.findAccessibilityNodeInfosByText(txt)
            if (!nodes.isNullOrEmpty()) {
                val bounds = android.graphics.Rect()
                val matched = nodes.firstOrNull { n ->
                    n.getBoundsInScreen(bounds)
                    bounds.left == target.boundsLeft && bounds.top == target.boundsTop
                } ?: nodes.first()
                nodes.filter { it !== matched }.forEach { it.recycle() }
                return matched
            }
        }

        return null
    }
}
