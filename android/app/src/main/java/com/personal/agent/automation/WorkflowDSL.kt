package com.personal.agent.automation

import com.squareup.moshi.JsonClass

// ─── Trigger ──────────────────────────────────────────────────────────────────

enum class TriggerType {
    NOTIFICATION_RECEIVED,   // notification arrives matching filters
    SCREEN_TYPE,             // foreground screen classified as given type
    APP_FOREGROUND,          // specific app comes to foreground
    APP_BACKGROUND,          // specific app goes to background
    TIME_OF_DAY,             // cron-like schedule
    MANUAL,                  // triggered by command
    BOOT,                    // device boots
    CHARGING_START,
    CHARGING_STOP,
    BATTERY_LOW,
    INTERVAL,                // every N minutes
}

@JsonClass(generateAdapter = true)
data class Trigger(
    val type:         TriggerType,
    val packageName:  String? = null,     // for NOTIFICATION_RECEIVED, APP_*
    val screenType:   String? = null,     // for SCREEN_TYPE
    val titleContains: String? = null,    // for NOTIFICATION_RECEIVED
    val bodyContains:  String? = null,    // for NOTIFICATION_RECEIVED
    val timeHour:     Int?    = null,     // for TIME_OF_DAY (0-23)
    val timeMinute:   Int?    = null,     // for TIME_OF_DAY (0-59)
    val intervalMinutes: Int? = null,     // for INTERVAL
    val days:         List<Int> = emptyList() // for TIME_OF_DAY (1=Mon, 7=Sun)
)

// ─── Condition ────────────────────────────────────────────────────────────────

enum class ConditionType {
    BATTERY_ABOVE,
    BATTERY_BELOW,
    NETWORK_CONNECTED,
    NETWORK_WIFI,
    SCREEN_TEXT_CONTAINS,
    SCREEN_TYPE_IS,
    TIME_BETWEEN,
    APP_INSTALLED,
    ALWAYS,
}

@JsonClass(generateAdapter = true)
data class Condition(
    val type:          ConditionType,
    val intValue:      Int?    = null,   // battery percent threshold
    val stringValue:   String? = null,   // text to find / package name / screen type
    val timeStart:     String? = null,   // HH:MM
    val timeEnd:       String? = null,   // HH:MM
    val negate:        Boolean = false   // invert the result
)

// ─── Action ───────────────────────────────────────────────────────────────────

enum class ActionType {
    // Accessibility
    CLICK, LONG_CLICK, TYPE, CLEAR_TEXT,
    SCROLL_FORWARD, SCROLL_BACKWARD, SWIPE,
    BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS,
    WAIT_ELEMENT, WAIT_TEXT, FOCUS,

    // Vision / OCR
    CAPTURE_SCREEN, OCR_TEXT,

    // System
    WAIT_MS, LAUNCH_APP, OPEN_URL,

    // Flow control
    IF_TEXT_CONTAINS, REPEAT,

    // Notification
    SEND_NOTIFICATION,

    // HTTP
    HTTP_POST,

    // Log
    LOG_EVENT,
}

@JsonClass(generateAdapter = true)
data class WorkflowAction(
    val type:               ActionType,
    // Accessibility targeting
    val targetText:         String? = null,
    val targetViewId:       String? = null,
    val targetDescription:  String? = null,
    val targetClass:        String? = null,
    // Text input
    val text:               String? = null,
    // Wait
    val waitMs:             Long    = 0,
    val timeoutMs:          Long    = 10_000,
    // Swipe
    val swipeStartX:        Float   = 0f,
    val swipeStartY:        Float   = 0f,
    val swipeEndX:          Float   = 0f,
    val swipeEndY:          Float   = 0f,
    // Flow control
    val condition:          String? = null,  // for IF_TEXT_CONTAINS
    val thenSteps:          List<String> = emptyList(),
    val elseSteps:          List<String> = emptyList(),
    val repeatCount:        Int     = 1,
    // App / URL
    val packageName:        String? = null,
    val url:                String? = null,
    // HTTP
    val httpUrl:            String? = null,
    val httpBodyJson:       String? = null,
    // Notification
    val notificationTitle:  String? = null,
    val notificationBody:   String? = null,
    // Log
    val logMessage:         String? = null,
    // Fallback gesture
    val useFallbackGesture: Boolean = false,
    val scrollDirection:    String  = "down"
)

// ─── Step ─────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class WorkflowStep(
    val id:          String,
    val name:        String = "",
    val action:      WorkflowAction,
    val onFail:      StepFailPolicy = StepFailPolicy.ABORT,
    val retryCount:  Int    = 0,
    val retryDelayMs: Long  = 500
)

enum class StepFailPolicy {
    ABORT,      // stop workflow
    CONTINUE,   // skip step and continue
    RETRY,      // retry up to retryCount times
}

// ─── Workflow ─────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class Workflow(
    val id:          String,
    val name:        String,
    val enabled:     Boolean,
    val version:     Int,
    val trigger:     Trigger,
    val conditions:  List<Condition> = emptyList(),
    val steps:       List<WorkflowStep>,
    val createdAt:   Long,
    val updatedAt:   Long
)

// ─── Run context ──────────────────────────────────────────────────────────────

/**
 * Mutable execution context passed through all steps of a single workflow run.
 * Steps can read/write variables; actions can resolve {{variable}} templates.
 */
data class RunContext(
    val runId:      String,
    val workflowId: String,
    val triggerId:  String?,
    val vars:       MutableMap<String, Any> = mutableMapOf(),
    val stepLog:    MutableList<StepLog>    = mutableListOf(),
    var currentStep: String? = null
) {
    fun resolve(template: String?): String? {
        if (template == null) return null
        var result = template
        vars.forEach { (k, v) -> result = result!!.replace("{{$k}}", v.toString()) }
        return result
    }
}

data class StepLog(
    val stepId:   String,
    val success:  Boolean,
    val message:  String,
    val durationMs: Long
)

// ─── Events (trigger bus) ─────────────────────────────────────────────────────

sealed class AgentEvent {
    data class NotificationEvent(
        val packageName: String,
        val title:       String?,
        val body:        String?,
        val sender:      String?
    ) : AgentEvent()

    data class ScreenTypeEvent(
        val screenType:  String,
        val packageName: String?
    ) : AgentEvent()

    data class AppForegroundEvent(
        val packageName: String,
        val foreground:  Boolean
    ) : AgentEvent()

    data class BatteryEvent(
        val percent: Int,
        val charging: Boolean
    ) : AgentEvent()

    object BootEvent : AgentEvent()
    object ManualTrigger : AgentEvent()
}
