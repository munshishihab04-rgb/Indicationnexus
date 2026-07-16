package com.personal.agent.accessibility

import android.provider.Settings
import android.util.Log
import com.personal.agent.core.modules.AgentCommand
import com.personal.agent.core.modules.AgentModule
import com.personal.agent.core.modules.CommandResult
import com.personal.agent.core.modules.HealthStatus
import com.personal.agent.core.modules.ModuleConfig
import com.personal.agent.core.modules.ModuleContext
import com.personal.agent.core.modules.ModuleHealth

private const val TAG = "AccessibilityEngine"

/**
 * Phase 5 — Accessibility Engine [AgentModule].
 *
 * Controls whether the accessibility service processes events and accepts actions.
 * The actual [AgentAccessibilityService] is Android-managed — this module acts as
 * the policy controller and command router.
 *
 * Commands handled:
 * - click, long_click, type, clear_text
 * - scroll_forward, scroll_backward, swipe
 * - back, home, recents, notifications, quick_settings
 * - wait_element, wait_text
 * - get_tree (returns current window snapshot as JSON)
 * - get_stats
 */
class AccessibilityEngine : AgentModule {

    override val id: String = MODULE_ID
    override val version: Int = 1

    private lateinit var ctx: ModuleContext
    private var lastErrorCode: String? = null
    private var startedAt: Long? = null
    private var actionsExecuted: Long = 0L
    private var actionsFailed: Long = 0L

    override suspend fun initialize(context: ModuleContext) {
        ctx = context
        Log.i(TAG, "AccessibilityEngine initialized")
    }

    override suspend fun start(config: ModuleConfig) {
        val permitted = checkPermission()
        if (!permitted) {
            Log.w(TAG, "Accessibility service not enabled — engine inactive")
            lastErrorCode = "permission_not_granted"
            return
        }
        AccessibilityEngineState.enabled = true
        startedAt = System.currentTimeMillis()
        lastErrorCode = null
        Log.i(TAG, "AccessibilityEngine started — service connected=${AccessibilityEngineState.connected}")
        ctx.logger.info(id, "engine_started", "Accessibility capture enabled")
    }

    override suspend fun stop(reason: String) {
        AccessibilityEngineState.enabled = false
        Log.i(TAG, "AccessibilityEngine stopped: $reason")
        ctx.logger.info(id, "engine_stopped", reason)
    }

    override suspend fun applyConfig(config: ModuleConfig) {
        if (!config.enabled && AccessibilityEngineState.enabled) stop("disabled_by_config")
        else if (config.enabled && !AccessibilityEngineState.enabled) start(config)
    }

    override suspend fun health(): ModuleHealth {
        val status = when {
            lastErrorCode != null                   -> HealthStatus.DEGRADED
            !AccessibilityEngineState.enabled       -> HealthStatus.STOPPED
            !AccessibilityEngineState.connected     -> HealthStatus.DEGRADED
            else                                    -> HealthStatus.HEALTHY
        }
        return ModuleHealth(
            id            = id,
            status        = status,
            lastSuccessAt = startedAt,
            lastErrorCode = lastErrorCode,
            queueDepth    = 0
        )
    }

    override suspend fun handleCommand(command: AgentCommand): CommandResult {
        if (!AccessibilityEngineState.enabled) {
            return CommandResult(command.id, false, errorCode = "engine_disabled")
        }

        val executor = AccessibilityEngineState.executor
            ?: return CommandResult(command.id, false, errorCode = "service_not_connected")

        return when (command.type) {
            "get_stats" -> CommandResult(
                commandId = command.id,
                success   = true,
                data      = mapOf(
                    "enabled"          to AccessibilityEngineState.enabled,
                    "connected"        to AccessibilityEngineState.connected,
                    "actionsExecuted"  to actionsExecuted,
                    "actionsFailed"    to actionsFailed
                )
            )

            "get_tree" -> {
                val service = AccessibilityEngineState.service
                if (service == null) {
                    CommandResult(command.id, false, errorCode = "service_not_connected")
                } else {
                    val root = service.rootInActiveWindow
                    val snapshot = AccessibilityTreeBuilder.build(root)
                    root?.recycle()
                    if (snapshot != null) {
                        CommandResult(
                            commandId = command.id,
                            success   = true,
                            data      = mapOf(
                                "packageName" to (snapshot.packageName ?: ""),
                                "nodeCount"   to AccessibilityTreeBuilder.flatten(snapshot).size,
                                "rootLabel"   to snapshot.label()
                            )
                        )
                    } else {
                        CommandResult(command.id, false, errorCode = "no_window")
                    }
                }
            }

            // Action commands — parse ActionCommand from payload
            else -> {
                val actionCmd = parseActionCommand(command) ?: return CommandResult(
                    commandId = command.id,
                    success   = false,
                    errorCode = "unknown_action_type"
                )

                ctx.logger.info(id, "action_start", "${actionCmd.type} target='${actionCmd.targetText}'")
                val result = executor.execute(actionCmd)

                if (result.success) {
                    actionsExecuted++
                    ctx.logger.info(id, "action_ok", result.message)
                } else {
                    actionsFailed++
                    lastErrorCode = result.errorCode
                    ctx.logger.warn(id, "action_fail", "${result.errorCode}: ${result.message}")
                }

                CommandResult(
                    commandId = command.id,
                    success   = result.success,
                    data      = mapOf("message" to result.message),
                    errorCode = result.errorCode
                )
            }
        }
    }

    // ─── ActionCommand parser ─────────────────────────────────────────────────

    private fun parseActionCommand(command: AgentCommand): ActionCommand? {
        val type = try {
            ActionType.valueOf(command.type.uppercase())
        } catch (_: IllegalArgumentException) {
            return null
        }
        val p = command.payload
        return ActionCommand(
            type                = type,
            targetText          = p["targetText"] as? String,
            targetViewId        = p["targetViewId"] as? String,
            targetClass         = p["targetClass"] as? String,
            targetDescription   = p["targetDescription"] as? String,
            text                = p["text"] as? String,
            timeoutMs           = (p["timeoutMs"] as? Number)?.toLong() ?: 10_000L,
            scrollDirection     = p["scrollDirection"] as? String ?: "down",
            swipeStartX         = (p["swipeStartX"] as? Number)?.toFloat() ?: 0f,
            swipeStartY         = (p["swipeStartY"] as? Number)?.toFloat() ?: 0f,
            swipeEndX           = (p["swipeEndX"] as? Number)?.toFloat() ?: 0f,
            swipeEndY           = (p["swipeEndY"] as? Number)?.toFloat() ?: 0f,
            swipeDurationMs     = (p["swipeDurationMs"] as? Number)?.toLong() ?: 300L,
            useFallbackGesture  = p["useFallbackGesture"] as? Boolean ?: false
        )
    }

    private fun checkPermission(): Boolean {
        val flat = Settings.Secure.getString(
            ctx.appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return flat.contains(ctx.appContext.packageName)
    }

    companion object {
        const val MODULE_ID = "accessibility_engine"
    }
}

/**
 * Shared state between [AgentAccessibilityService] and [AccessibilityEngine].
 * The service is Android-managed — state passes through this singleton.
 */
object AccessibilityEngineState {
    @Volatile var enabled:   Boolean           = false
    @Volatile var connected: Boolean           = false
    @Volatile var service:   AgentAccessibilityService? = null
    @Volatile var executor:  ActionExecutor?   = null
}
