package com.personal.agent.notifications

import android.provider.Settings
import android.util.Log
import com.personal.agent.core.modules.AgentCommand
import com.personal.agent.core.modules.AgentModule
import com.personal.agent.core.modules.CommandResult
import com.personal.agent.core.modules.HealthStatus
import com.personal.agent.core.modules.ModuleConfig
import com.personal.agent.core.modules.ModuleContext
import com.personal.agent.core.modules.ModuleHealth

private const val TAG = "NotificationEngine"

/**
 * Phase 4 — Notification Engine [AgentModule].
 *
 * Controls the lifecycle of notification capture:
 * - On start(): enables capture via [NotificationEngineState], schedules upload worker.
 * - On stop(): disables capture, cancels upload worker.
 * - Handles commands: get_stats, flush_upload, set_enabled.
 * - Reports health including listener connection state.
 *
 * Note: [AgentNotificationListener] is Android-managed (bound by the OS when the user
 * grants access). This module does NOT start/stop the listener — it controls whether
 * events are processed when they arrive.
 */
class NotificationEngine : AgentModule {

    override val id: String = MODULE_ID
    override val version: Int = 1

    private lateinit var ctx: ModuleContext
    private var lastErrorCode: String? = null
    private var startedAt: Long? = null

    override suspend fun initialize(context: ModuleContext) {
        ctx = context
        Log.i(TAG, "NotificationEngine initialized")
    }

    override suspend fun start(config: ModuleConfig) {
        Log.i(TAG, "NotificationEngine starting")

        val permOk = checkPermission()
        if (!permOk) {
            Log.w(TAG, "Notification listener permission not granted — engine inactive")
            lastErrorCode = "permission_not_granted"
            return
        }

        NotificationEngineState.enabled = true
        startedAt = System.currentTimeMillis()
        lastErrorCode = null

        // Schedule upload worker
        NotificationUploadWorker.schedule(ctx.appContext)

        ctx.logger.info(
            module  = id,
            event   = "engine_started",
            message = "Notification capture enabled"
        )
        Log.i(TAG, "NotificationEngine started — listener connected=${NotificationEngineState.connected}")
    }

    override suspend fun stop(reason: String) {
        Log.i(TAG, "NotificationEngine stopping: $reason")
        NotificationEngineState.enabled = false

        // Cancel upload worker
        androidx.work.WorkManager.getInstance(ctx.appContext)
            .cancelUniqueWork("agent_notification_upload")

        ctx.logger.info(
            module  = id,
            event   = "engine_stopped",
            message = "Notification capture disabled: $reason"
        )
    }

    override suspend fun applyConfig(config: ModuleConfig) {
        if (!config.enabled && NotificationEngineState.enabled) {
            stop("disabled_by_config")
        } else if (config.enabled && !NotificationEngineState.enabled) {
            start(config)
        }
    }

    override suspend fun health(): ModuleHealth {
        val status = when {
            lastErrorCode != null                  -> HealthStatus.DEGRADED
            !NotificationEngineState.enabled       -> HealthStatus.STOPPED
            !NotificationEngineState.connected     -> HealthStatus.DEGRADED
            else                                   -> HealthStatus.HEALTHY
        }
        return ModuleHealth(
            id             = id,
            status         = status,
            lastSuccessAt  = startedAt,
            lastErrorCode  = lastErrorCode,
            queueDepth     = try {
                NotificationQueue(ctx.db).pendingCount()
            } catch (_: Exception) { 0 }
        )
    }

    override suspend fun handleCommand(command: AgentCommand): CommandResult {
        return when (command.type) {
            "get_stats" -> {
                val pending = try { NotificationQueue(ctx.db).pendingCount() } catch (_: Exception) { -1 }
                CommandResult(
                    commandId = command.id,
                    success   = true,
                    data      = mapOf(
                        "enabled"   to NotificationEngineState.enabled,
                        "connected" to NotificationEngineState.connected,
                        "received"  to NotificationEngineState.received,
                        "pending"   to pending
                    )
                )
            }
            "flush_upload" -> {
                NotificationUploadWorker.schedule(ctx.appContext, intervalMinutes = 1)
                CommandResult(commandId = command.id, success = true,
                    data = mapOf("scheduled" to true))
            }
            "set_enabled" -> {
                val enable = command.payload["enabled"] as? Boolean ?: true
                if (enable) start(ModuleConfig(enabled = true))
                else stop("command_disable")
                CommandResult(commandId = command.id, success = true,
                    data = mapOf("enabled" to enable))
            }
            else -> CommandResult(
                commandId = command.id,
                success   = false,
                errorCode = "unknown_command"
            )
        }
    }

    // ─── Permission check ─────────────────────────────────────────────────────

    private fun checkPermission(): Boolean {
        val flat = Settings.Secure.getString(
            ctx.appContext.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(ctx.appContext.packageName)
    }

    companion object {
        const val MODULE_ID = "notification_engine"
    }
}
