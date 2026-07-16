package com.personal.agent.accessibility

import android.util.Log

private const val TAG = "NodeFinder"

/**
 * Finds nodes in a [UiNodeSnapshot] tree by various criteria.
 * All methods work on the immutable snapshot — no live node references.
 */
object NodeFinder {

    /**
     * Finds all nodes matching a predicate.
     * Traverses breadth-first to find the most relevant (shallowest) match first.
     */
    fun findAll(
        root: UiNodeSnapshot,
        predicate: (UiNodeSnapshot) -> Boolean
    ): List<UiNodeSnapshot> {
        val flat = AccessibilityTreeBuilder.flatten(root)
        return flat.filter(predicate)
    }

    /** Finds the first matching node, or null if not found. */
    fun findFirst(
        root: UiNodeSnapshot,
        predicate: (UiNodeSnapshot) -> Boolean
    ): UiNodeSnapshot? = findAll(root, predicate).firstOrNull()

    // ─── Specific matchers ────────────────────────────────────────────────────

    /** Finds nodes whose text contains [query] (case-insensitive). */
    fun byText(root: UiNodeSnapshot, query: String): List<UiNodeSnapshot> =
        findAll(root) { node ->
            node.text?.contains(query, ignoreCase = true) == true
        }

    /** Finds nodes whose text exactly matches [text] (case-insensitive). */
    fun byExactText(root: UiNodeSnapshot, text: String): UiNodeSnapshot? =
        findFirst(root) { node ->
            node.text?.equals(text, ignoreCase = true) == true
        }

    /** Finds a node by its resource-id (full or short form). */
    fun byViewId(root: UiNodeSnapshot, viewId: String): UiNodeSnapshot? =
        findFirst(root) { node ->
            node.viewId == viewId || node.id == viewId
        }

    /** Finds nodes whose content description contains [desc] (case-insensitive). */
    fun byDescription(root: UiNodeSnapshot, desc: String): List<UiNodeSnapshot> =
        findAll(root) { node ->
            node.contentDescription?.contains(desc, ignoreCase = true) == true
        }

    /** Finds all nodes of a given class name (e.g. "android.widget.Button"). */
    fun byClass(root: UiNodeSnapshot, className: String): List<UiNodeSnapshot> =
        findAll(root) { node ->
            node.className?.endsWith(className, ignoreCase = true) == true
        }

    /** Finds all clickable nodes. */
    fun clickable(root: UiNodeSnapshot): List<UiNodeSnapshot> =
        findAll(root) { it.clickable && it.enabled }

    /** Finds all editable (input field) nodes. */
    fun editable(root: UiNodeSnapshot): List<UiNodeSnapshot> =
        findAll(root) { it.editable && it.enabled }

    /** Finds all scrollable nodes. */
    fun scrollable(root: UiNodeSnapshot): List<UiNodeSnapshot> =
        findAll(root) { it.scrollable && it.enabled }

    /**
     * Finds the best node matching an [ActionCommand] target spec.
     * Priority: viewId → exact text → text contains → content description.
     */
    fun findTarget(root: UiNodeSnapshot, cmd: ActionCommand): UiNodeSnapshot? {
        // 1. by viewId (most precise)
        cmd.targetViewId?.let { vid ->
            byViewId(root, vid)?.let { return it }
        }
        // 2. by exact text
        cmd.targetText?.let { txt ->
            byExactText(root, txt)?.let { return it }
        }
        // 3. by text contains
        cmd.targetText?.let { txt ->
            byText(root, txt).firstOrNull { it.clickable || it.enabled }?.let { return it }
            byText(root, txt).firstOrNull()?.let { return it }
        }
        // 4. by content description
        cmd.targetDescription?.let { desc ->
            byDescription(root, desc).firstOrNull()?.let { return it }
        }
        // 5. by class name
        cmd.targetClass?.let { cls ->
            byClass(root, cls).firstOrNull { it.clickable }?.let { return it }
        }

        Log.d(TAG, "No node found for: viewId=${cmd.targetViewId} text=${cmd.targetText}")
        return null
    }
}
