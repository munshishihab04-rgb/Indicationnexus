package com.personal.agent.accessibility

/**
 * Global mutable foreground package name.
 * Updated by [AgentAccessibilityService] on every TYPE_WINDOW_STATE_CHANGED event.
 * Read by VisionEngine and ScreenRecord without taking a service reference.
 */
@Volatile
var foregroundPackage: String? = null
