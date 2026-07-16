package com.personal.agent.automation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import com.personal.agent.accessibility.AccessibilityEngineState
import com.personal.agent.accessibility.AccessibilityTreeBuilder
import com.personal.agent.accessibility.NodeFinder
import com.personal.agent.accessibility.foregroundPackage
import com.personal.agent.vision.ScreenType
import com.personal.agent.vision.VisionAnalyzer
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val TAG = "TriggerEvaluator"

/**
 * Evaluates whether a [Trigger] fires given an [AgentEvent].
 */
object TriggerEvaluator {

    fun matches(trigger: Trigger, event: AgentEvent): Boolean = when (trigger.type) {

        TriggerType.MANUAL ->
            event is AgentEvent.ManualTrigger

        TriggerType.BOOT ->
            event is AgentEvent.BootEvent

        TriggerType.NOTIFICATION_RECEIVED -> {
            event is AgentEvent.NotificationEvent && run {
                trigger.packageName?.let { event.packageName == it } ?: true &&
                trigger.titleContains?.let { event.title?.contains(it, ignoreCase = true) == true } ?: true &&
                trigger.bodyContains?.let { event.body?.contains(it, ignoreCase = true) == true } ?: true
            }
        }

        TriggerType.SCREEN_TYPE -> {
            event is AgentEvent.ScreenTypeEvent &&
            trigger.screenType?.equals(event.screenType, ignoreCase = true) == true
        }

        TriggerType.APP_FOREGROUND -> {
            event is AgentEvent.AppForegroundEvent &&
            event.foreground &&
            trigger.packageName?.let { it == event.packageName } ?: true
        }

        TriggerType.APP_BACKGROUND -> {
            event is AgentEvent.AppForegroundEvent &&
            !event.foreground &&
            trigger.packageName?.let { it == event.packageName } ?: true
        }

        TriggerType.CHARGING_START -> {
            event is AgentEvent.BatteryEvent && event.charging
        }

        TriggerType.CHARGING_STOP -> {
            event is AgentEvent.BatteryEvent && !event.charging
        }

        TriggerType.BATTERY_LOW -> {
            event is AgentEvent.BatteryEvent && event.percent <= 20
        }

        TriggerType.TIME_OF_DAY -> {
            // TIME_OF_DAY is checked by scheduler, not by event matching
            // Returns true to allow scheduler-driven evaluation
            event is AgentEvent.ManualTrigger
        }

        TriggerType.INTERVAL -> {
            // INTERVAL is checked by scheduler
            event is AgentEvent.ManualTrigger
        }
    }
}

// ─── ConditionChecker ─────────────────────────────────────────────────────────

/**
 * Evaluates a list of [Condition]s against the current device state.
 * All conditions must pass (AND logic) for the workflow to proceed.
 */
object ConditionChecker {

    fun allPass(conditions: List<Condition>, context: Context): Boolean =
        conditions.all { evaluate(it, context) }

    private fun evaluate(condition: Condition, context: Context): Boolean {
        val result = evaluateRaw(condition, context)
        return if (condition.negate) !result else result
    }

    private fun evaluateRaw(condition: Condition, context: Context): Boolean {
        return when (condition.type) {

        ConditionType.ALWAYS -> true

        ConditionType.BATTERY_ABOVE -> {
            val pct = batteryPercent(context)
            condition.intValue?.let { pct >= it } ?: true
        }

        ConditionType.BATTERY_BELOW -> {
            val pct = batteryPercent(context)
            condition.intValue?.let { pct < it } ?: true
        }

        ConditionType.NETWORK_CONNECTED -> {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } != null
        }

        ConditionType.NETWORK_WIFI -> {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }

        ConditionType.SCREEN_TEXT_CONTAINS -> {
            val query = condition.stringValue ?: return false
            val service = AccessibilityEngineState.service ?: return false
            try {
                val root = service.rootInActiveWindow ?: return false
                val snapshot = AccessibilityTreeBuilder.build(root)
                root.recycle()
                if (snapshot == null) return false
                NodeFinder.byText(snapshot, query).isNotEmpty()
            } catch (_: Exception) { false }
        }

        ConditionType.SCREEN_TYPE_IS -> {
            val expected = condition.stringValue ?: return false
            val pkg = foregroundPackage
            val classification = VisionAnalyzer.classify(pkg, "", com.personal.agent.vision.TreeHints())
            classification.screenType.name.equals(expected, ignoreCase = true)
        }

        ConditionType.TIME_BETWEEN -> {
            val start = condition.timeStart?.let { parseTime(it) } ?: return true
            val end   = condition.timeEnd?.let { parseTime(it) }   ?: return true
            val now   = LocalTime.now()
            if (start.isBefore(end)) now.isAfter(start) && now.isBefore(end)
            else now.isAfter(start) || now.isBefore(end) // crosses midnight
        }

        ConditionType.APP_INSTALLED -> {
            val pkg = condition.stringValue ?: return false
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) { false }
        }
        }  // end return when
    }  // end evaluateRaw

    private fun batteryPercent(context: Context): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level * 100 / scale else 100
    }

    private fun parseTime(s: String): LocalTime =
        LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm"))
}