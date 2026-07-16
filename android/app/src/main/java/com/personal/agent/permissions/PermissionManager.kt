package com.personal.agent.permissions

import android.util.Log
import com.personal.agent.core.db.PermissionEntity
import com.personal.agent.core.modules.AgentCommand
import com.personal.agent.core.modules.AgentModule
import com.personal.agent.core.modules.CommandResult
import com.personal.agent.core.modules.HealthStatus
import com.personal.agent.core.modules.ModuleConfig
import com.personal.agent.core.modules.ModuleContext
import com.personal.agent.core.modules.ModuleHealth

private const val TAG = "PermissionManager"

/**
 * Phase 2 — Permission Manager module.
 *
 * Responsibilities:
 * - Check all required Android permissions and special settings on startup and periodically.
 * - Persist permission state snapshots to the Room `permissions` table.
 * - Expose permission state to other modules via [checkAll].
 * - Disable dependent modules when required permissions are absent.
 *
 * This module does NOT request permissions — it only reads their current state
 * and reports it. Permission requests must go through Android's standard flow
 * (the user taps a button in MainActivity).
 */
class PermissionManager : AgentModule {

    override val id: String = MODULE_ID
    override val version: Int = 1

    private lateinit var ctx: ModuleContext
    private lateinit var checker: PermissionChecker
    private var lastCheckAt: Long? = null
    private var lastError: String? = null

    override suspend fun initialize(context: ModuleContext) {
        ctx = context
        checker = PermissionChecker(context.appContext)
        Log.i(TAG, "PermissionManager initialized")
    }

    override suspend fun start(config: ModuleConfig) {
        Log.i(TAG, "PermissionManager started")
        checkAndPersist()
    }

    override suspend fun stop(reason: String) {
        Log.i(TAG, "PermissionManager stopped: $reason")
    }

    override suspend fun applyConfig(config: ModuleConfig) {
        // No interval config yet — checks on every start() call
    }

    override suspend fun health(): ModuleHealth {
        val status = if (lastError == null) HealthStatus.HEALTHY else HealthStatus.DEGRADED
        return ModuleHealth(
            id = id,
            status = status,
            lastSuccessAt = lastCheckAt,
            lastErrorCode = lastError
        )
    }

    override suspend fun handleCommand(command: AgentCommand): CommandResult {
        return when (command.type) {
            "check_permissions" -> {
                val states = checkAndPersist()
                CommandResult(
                    commandId = command.id,
                    success = true,
                    data = states.mapValues { (_, v) -> v as Any }
                )
            }
            else -> CommandResult(
                commandId = command.id,
                success = false,
                errorCode = "unknown_command"
            )
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    /**
     * Reads all permission states, persists them to Room, and returns the map.
     */
    private suspend fun checkAndPersist(): Map<String, Boolean> {
        return try {
            val states = checker.checkAll()
            val now = System.currentTimeMillis()

            val entities = states.map { (key, granted) ->
                PermissionEntity(
                    key = key,
                    granted = granted,
                    requiredBy = "[]",
                    lastCheckedAt = now,
                    lastPromptedAt = null,
                    settingsAction = permissionSettingsAction(key)
                )
            }

            ctx.db.permissionDao().upsertAll(entities)
            lastCheckAt = now
            lastError = null

            Log.i(TAG, "Permissions checked: ${states.filter { it.value }.size}/${states.size} granted")
            ctx.logger.info(
                module = id,
                event = "permissions_checked",
                message = "${states.filter { it.value }.size}/${states.size} granted",
                data = states.mapValues { (_, v) -> v as Any }
            )

            states
        } catch (e: Exception) {
            lastError = e.message?.take(100)
            Log.e(TAG, "Permission check failed: ${e.message}")
            emptyMap()
        }
    }

    private fun permissionSettingsAction(key: String): String? = when (key) {
        "notification_listener" -> "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        "accessibility"         -> "android.settings.ACCESSIBILITY_SETTINGS"
        "battery_optimization"  -> "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
        else                    -> null
    }

    companion object {
        const val MODULE_ID = "permission_manager"
    }
}
