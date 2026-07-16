package com.personal.agent.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

private const val TAG = "TreeBuilder"
private const val MAX_DEPTH = 30        // avoid infinite recursion on deeply nested UIs
private const val MAX_NODES = 2_000     // cap total nodes for large screens

/**
 * Converts a live [AccessibilityNodeInfo] tree into an immutable [UiNodeSnapshot] tree.
 * Recycles all live nodes after snapshot to prevent memory leaks.
 */
object AccessibilityTreeBuilder {

    /**
     * Builds a snapshot of the full accessibility tree rooted at [root].
     * Returns null if root is null.
     *
     * @param maxDepth  Maximum tree depth to traverse (default 30).
     * @param maxNodes  Maximum total nodes to capture (default 2000).
     */
    fun build(
        root: AccessibilityNodeInfo?,
        maxDepth: Int = MAX_DEPTH,
        maxNodes: Int = MAX_NODES
    ): UiNodeSnapshot? {
        root ?: return null
        val counter = intArrayOf(0)
        return buildNode(root, depth = 0, maxDepth = maxDepth, maxNodes = maxNodes, counter = counter)
    }

    private fun buildNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int,
        maxNodes: Int,
        counter: IntArray
    ): UiNodeSnapshot? {
        if (counter[0] >= maxNodes) {
            Log.w(TAG, "Max node count ($maxNodes) reached — truncating tree")
            return null
        }
        counter[0]++

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        val fullViewId = node.viewIdResourceName
        val shortViewId = fullViewId?.substringAfterLast("/")

        val children = mutableListOf<UiNodeSnapshot>()
        if (depth < maxDepth) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    val childSnapshot = buildNode(child, depth + 1, maxDepth, maxNodes, counter)
                    if (childSnapshot != null) children.add(childSnapshot)
                } finally {
                    child.recycle()
                }
            }
        }

        return UiNodeSnapshot(
            id                 = fullViewId,
            text               = node.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            contentDescription = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() },
            viewId             = shortViewId,
            className          = node.className?.toString(),
            packageName        = node.packageName?.toString(),
            boundsLeft         = bounds.left,
            boundsTop          = bounds.top,
            boundsRight        = bounds.right,
            boundsBottom       = bounds.bottom,
            clickable          = node.isClickable,
            longClickable      = node.isLongClickable,
            editable           = node.isEditable,
            scrollable         = node.isScrollable,
            enabled            = node.isEnabled,
            checked            = node.isChecked,
            selected           = node.isSelected,
            focusable          = node.isFocusable,
            depth              = depth,
            childCount         = node.childCount,
            children           = children
        )
    }

    /** Flattens a snapshot tree into a list (breadth-first). */
    fun flatten(root: UiNodeSnapshot): List<UiNodeSnapshot> {
        val result = mutableListOf<UiNodeSnapshot>()
        val queue = ArrayDeque<UiNodeSnapshot>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            result.add(node)
            queue.addAll(node.children)
        }
        return result
    }
}
