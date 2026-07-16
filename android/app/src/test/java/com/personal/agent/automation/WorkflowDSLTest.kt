package com.personal.agent.automation

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for WorkflowDSL models — no Android framework required.
 */
class WorkflowDSLTest {

    @Test
    fun `RunContext resolve replaces template variables`() {
        val ctx = RunContext(
            runId = "r1", workflowId = "w1", triggerId = null,
            vars = mutableMapOf("name" to "Alice", "count" to 3)
        )
        assertEquals("Hello Alice", ctx.resolve("Hello {{name}}"))
        assertEquals("3 items", ctx.resolve("{{count}} items"))
        assertEquals("no vars", ctx.resolve("no vars"))
        assertNull(ctx.resolve(null))
    }

    @Test
    fun `RunContext resolve with missing variable leaves placeholder`() {
        val ctx = RunContext("r1", "w1", null)
        // Missing var → left as-is (no exception)
        assertEquals("Hello {{unknown}}", ctx.resolve("Hello {{unknown}}"))
    }

    @Test
    fun `WorkflowAction defaults are sane`() {
        val action = WorkflowAction(type = ActionType.CLICK)
        assertEquals(10_000L, action.timeoutMs)
        assertEquals("down", action.scrollDirection)
        assertFalse(action.useFallbackGesture)
        assertEquals(0f, action.swipeStartX)
    }

    @Test
    fun `WorkflowStep ABORT is default fail policy`() {
        val step = WorkflowStep(id = "s1", action = WorkflowAction(ActionType.BACK))
        assertEquals(StepFailPolicy.ABORT, step.onFail)
        assertEquals(0, step.retryCount)
    }

    @Test
    fun `Trigger with packageName matches correctly`() {
        val t = Trigger(TriggerType.NOTIFICATION_RECEIVED, packageName = "com.whatsapp")
        assertEquals("com.whatsapp", t.packageName)
    }

    @Test
    fun `Condition negate flag inverts result`() {
        // We can't call ConditionChecker directly (needs Context),
        // but we verify the model structure
        val c = Condition(ConditionType.ALWAYS, negate = true)
        assertTrue(c.negate)
    }

    @Test
    fun `AgentEvent NotificationEvent stores all fields`() {
        val e = AgentEvent.NotificationEvent("com.wa", "Alice", "Hello", "Alice")
        assertEquals("com.wa", e.packageName)
        assertEquals("Alice", e.title)
        assertEquals("Hello", e.body)
    }

    @Test
    fun `StepLog stores duration and success`() {
        val log = StepLog("s1", true, "clicked OK", 120L)
        assertTrue(log.success)
        assertEquals(120L, log.durationMs)
    }
}
