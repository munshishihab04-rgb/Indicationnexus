package com.personal.agent.automation

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TriggerEvaluator — no Android framework required.
 */
class TriggerEvaluatorTest {

    @Test
    fun `MANUAL trigger matches ManualTrigger event`() {
        val trigger = Trigger(type = TriggerType.MANUAL)
        assertTrue(TriggerEvaluator.matches(trigger, AgentEvent.ManualTrigger))
    }

    @Test
    fun `MANUAL trigger does not match NotificationEvent`() {
        val trigger = Trigger(type = TriggerType.MANUAL)
        assertFalse(TriggerEvaluator.matches(trigger, AgentEvent.NotificationEvent("com.test", null, null, null)))
    }

    @Test
    fun `NOTIFICATION_RECEIVED matches correct package`() {
        val trigger = Trigger(type = TriggerType.NOTIFICATION_RECEIVED, packageName = "com.whatsapp")
        val event   = AgentEvent.NotificationEvent("com.whatsapp", "Alice", "Hello", "Alice")
        assertTrue(TriggerEvaluator.matches(trigger, event))
    }

    @Test
    fun `NOTIFICATION_RECEIVED does not match wrong package`() {
        val trigger = Trigger(type = TriggerType.NOTIFICATION_RECEIVED, packageName = "com.whatsapp")
        val event   = AgentEvent.NotificationEvent("org.telegram.messenger", "Bot", "Hi", null)
        assertFalse(TriggerEvaluator.matches(trigger, event))
    }

    @Test
    fun `NOTIFICATION_RECEIVED title filter works`() {
        val trigger = Trigger(type = TriggerType.NOTIFICATION_RECEIVED, titleContains = "OTP")
        val eventMatch    = AgentEvent.NotificationEvent("com.bank", "Your OTP is 1234", null, null)
        val eventNoMatch  = AgentEvent.NotificationEvent("com.bank", "Welcome back", null, null)
        assertTrue(TriggerEvaluator.matches(trigger, eventMatch))
        assertFalse(TriggerEvaluator.matches(trigger, eventNoMatch))
    }

    @Test
    fun `SCREEN_TYPE matches correct screen type`() {
        val trigger = Trigger(type = TriggerType.SCREEN_TYPE, screenType = "chat")
        val event   = AgentEvent.ScreenTypeEvent("chat", "com.whatsapp")
        assertTrue(TriggerEvaluator.matches(trigger, event))
    }

    @Test
    fun `SCREEN_TYPE is case-insensitive`() {
        val trigger = Trigger(type = TriggerType.SCREEN_TYPE, screenType = "CHAT")
        val event   = AgentEvent.ScreenTypeEvent("chat", "com.whatsapp")
        assertTrue(TriggerEvaluator.matches(trigger, event))
    }

    @Test
    fun `APP_FOREGROUND matches correct package`() {
        val trigger = Trigger(type = TriggerType.APP_FOREGROUND, packageName = "com.whatsapp")
        val event   = AgentEvent.AppForegroundEvent("com.whatsapp", foreground = true)
        assertTrue(TriggerEvaluator.matches(trigger, event))
    }

    @Test
    fun `APP_FOREGROUND does not match background event`() {
        val trigger = Trigger(type = TriggerType.APP_FOREGROUND, packageName = "com.whatsapp")
        val event   = AgentEvent.AppForegroundEvent("com.whatsapp", foreground = false)
        assertFalse(TriggerEvaluator.matches(trigger, event))
    }

    @Test
    fun `BOOT trigger matches BootEvent`() {
        val trigger = Trigger(type = TriggerType.BOOT)
        assertTrue(TriggerEvaluator.matches(trigger, AgentEvent.BootEvent))
    }

    @Test
    fun `BATTERY_LOW triggers when percent le 20`() {
        val trigger = Trigger(type = TriggerType.BATTERY_LOW)
        assertTrue(TriggerEvaluator.matches(trigger, AgentEvent.BatteryEvent(15, false)))
        assertTrue(TriggerEvaluator.matches(trigger, AgentEvent.BatteryEvent(20, false)))
        assertFalse(TriggerEvaluator.matches(trigger, AgentEvent.BatteryEvent(21, false)))
    }
}
