package com.personal.agent.accessibility

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for NodeFinder — pure logic, no Android framework.
 */
class NodeFinderTest {

    private fun makeNode(
        text: String? = null,
        viewId: String? = null,
        id: String? = viewId?.let { "com.test:id/$it" },
        className: String? = null,
        contentDescription: String? = null,
        clickable: Boolean = false,
        editable: Boolean = false,
        scrollable: Boolean = false,
        enabled: Boolean = true,
        children: List<UiNodeSnapshot> = emptyList()
    ) = UiNodeSnapshot(
        id = id, text = text, contentDescription = contentDescription,
        viewId = viewId, className = className, packageName = "com.test",
        boundsLeft = 0, boundsTop = 0, boundsRight = 100, boundsBottom = 50,
        clickable = clickable, longClickable = false, editable = editable,
        scrollable = scrollable, enabled = enabled, checked = false,
        selected = false, focusable = true, depth = 0,
        childCount = children.size, children = children
    )

    @Test
    fun `byText finds node with matching text`() {
        val root = makeNode(children = listOf(
            makeNode(text = "Hello World"),
            makeNode(text = "Goodbye")
        ))
        val results = NodeFinder.byText(root, "hello")
        assertEquals(1, results.size)
        assertEquals("Hello World", results[0].text)
    }

    @Test
    fun `byText is case-insensitive`() {
        val root = makeNode(children = listOf(makeNode(text = "SEND MESSAGE")))
        val results = NodeFinder.byText(root, "send")
        assertEquals(1, results.size)
    }

    @Test
    fun `byExactText returns null for partial match`() {
        val root = makeNode(children = listOf(makeNode(text = "Send Message")))
        assertNull(NodeFinder.byExactText(root, "Send"))
        assertNotNull(NodeFinder.byExactText(root, "Send Message"))
    }

    @Test
    fun `byViewId finds by short form`() {
        val root = makeNode(children = listOf(
            makeNode(viewId = "btn_send", id = "com.test:id/btn_send")
        ))
        assertNotNull(NodeFinder.byViewId(root, "btn_send"))
        assertNotNull(NodeFinder.byViewId(root, "com.test:id/btn_send"))
    }

    @Test
    fun `clickable returns only clickable+enabled nodes`() {
        val root = makeNode(children = listOf(
            makeNode(text = "A", clickable = true, enabled = true),
            makeNode(text = "B", clickable = true, enabled = false),
            makeNode(text = "C", clickable = false, enabled = true)
        ))
        val results = NodeFinder.clickable(root)
        assertEquals(1, results.size)
        assertEquals("A", results[0].text)
    }

    @Test
    fun `findTarget uses viewId over text`() {
        val target = makeNode(text = "Submit", viewId = "btn_submit")
        val decoy  = makeNode(text = "Submit", viewId = "btn_other")
        val root   = makeNode(children = listOf(decoy, target))
        val cmd = ActionCommand(
            type = ActionType.CLICK,
            targetViewId = "btn_submit",
            targetText   = "Submit"
        )
        val found = NodeFinder.findTarget(root, cmd)
        assertEquals("btn_submit", found?.viewId)
    }

    @Test
    fun `findAll returns empty list when no match`() {
        val root = makeNode(children = listOf(makeNode(text = "Unrelated")))
        val results = NodeFinder.byText(root, "xyz123")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `flatten returns all nodes including root`() {
        val child1 = makeNode(text = "c1")
        val child2 = makeNode(text = "c2")
        val root   = makeNode(children = listOf(child1, child2))
        val flat   = AccessibilityTreeBuilder.flatten(root)
        assertEquals(3, flat.size) // root + 2 children
    }

    @Test
    fun `UiNodeSnapshot centerX and centerY computed correctly`() {
        val node = makeNode().copy(
            boundsLeft = 10, boundsTop = 20, boundsRight = 110, boundsBottom = 60
        )
        assertEquals(60, node.centerX)
        assertEquals(40, node.centerY)
    }
}
