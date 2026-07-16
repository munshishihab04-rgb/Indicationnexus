package com.personal.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

private const val TAG = "AccessibilityService"

/**
 * Phase 5 — Accessibility Service.
 * Enabled by user: Android Settings → Accessibility → Personal Agent.
 *
 * Wires the live [AccessibilityService] into [AccessibilityEngineState] so that
 * [AccessibilityEngine] and [ActionExecutor] can use it.
 *
 * Event forwarding: currently logs foreground app changes only.
 * Phase 7 (Automation Engine) will add trigger evaluation.
 */
class AgentAccessibilityService : AccessibilityService() {

    private var executor: ActionExecutor? = null

    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility service connected")

        // Configure what we want to observe
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_CLICKED or
                         AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
        }

        // Wire into state
        executor = ActionExecutor(this)
        AccessibilityEngineState.service   = this
        AccessibilityEngineState.executor  = executor
        AccessibilityEngineState.connected = true
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        Log.i(TAG, "Accessibility service destroyed")
        AccessibilityEngineState.service   = null
        AccessibilityEngineState.executor  = null
        AccessibilityEngineState.connected = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!AccessibilityEngineState.enabled) return

        // Forward window state changes for foreground app tracking (Phase 7)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank()) {
                foregroundPackage = pkg
            }
        }
    }
}
