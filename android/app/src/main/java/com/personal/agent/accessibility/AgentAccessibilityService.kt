package com.personal.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility Engine — Phase 5.
 * Enabled by user in Android Settings > Accessibility > Personal Agent.
 * Provides UI automation: click, type, swipe, scroll, tree snapshot.
 *
 * Implementation: Phase 5 — not yet active.
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility service connected")
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // Phase 5: forward to Automation Engine trigger evaluator
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    companion object { private const val TAG = "AccessibilityService" }
}
