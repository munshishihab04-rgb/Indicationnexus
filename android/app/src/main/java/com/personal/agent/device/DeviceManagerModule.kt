package com.personal.agent.device

import android.util.Log
import com.personal.agent.core.modules.AgentCommand
import com.personal.agent.core.modules.AgentModule
import com.personal.agent.core.modules.CommandResult
import com.personal.agent.core.modules.HealthStatus
import com.personal.agent.core.modules.ModuleConfig
import com.personal.agent.core.modules.ModuleContext
import com.personal.agent.core.modules.ModuleHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DeviceManagerModule"

/**
 * Phase 3 — Device Manager as a full [AgentModule].
 *
 * Responsibilities:
 * - Maintains device identity (UUID generated on first run).
 * - Sends heartbeat on start() and handles heartbeat command.
 * - Reports module health based on last heartbeat result.
 * - Handles remote commands: heartbeat, get_inventory.
 *
 * The actual API calls delegate to [DeviceManager] which holds the static helpers.
 */
class DeviceManagerModule : AgentModule {

    override val id: String = MODULE_ID
    override val version: Int = 1

    private lateinit var ctx: ModuleContext
    private var lastHeartbeatAt: Long? = null
    private var lastErrorCode: String? = null
    private var configVersion: Int = 0

    override suspend fun initialize(context: ModuleContext) {
        ctx = context
        Log.i(TAG, "DeviceManagerModule initialized")
    }

    override suspend fun start(config: ModuleConfig) {
        Log.i(TAG, "DeviceManagerModule started")
        sendHeartbeat()
    }

    override suspend fun stop(reason: String) {
        Log.i(TAG, "DeviceManagerModule stopped: $reason")
    }

    override suspend fun applyConfig(config: ModuleConfig) {
        // No module-specific config currently — intervals come from remote config
    }

    override suspend fun health(): ModuleHealth {
        val status = when {
            lastErrorCode != null                                     -> HealthStatus.DEGRADED
            lastHeartbeatAt == null                                   -> HealthStatus.STOPPED
            System.currentTimeMillis() - lastHeartbeatAt!! > 120_000 -> HealthStatus.DEGRADED
            else                                                      -> HealthStatus.HEALTHY
        }
        return ModuleHealth(
            id = id,
            status = status,
            lastSuccessAt = lastHeartbeatAt,
            lastErrorCode = lastErrorCode
        )
    }

    override suspend fun handleCommand(command: AgentCommand): CommandResult {
        return when (command.type) {
            "heartbeat" -> {
                val ok = sendHeartbeat()
                CommandResult(
                    commandId = command.id,
                    success = ok,
                    errorCode = if (ok) null else "heartbeat_failed"
                )
            }
            "get_inventory" -> {
                val deviceId = DeviceManager.getOrCreateDeviceId(ctx.appContext)
                CommandResult(
                    commandId = command.id,
                    success = true,
                    data = mapOf(
                        "deviceId" to deviceId,
                        "model" to android.os.Build.MODEL,
                        "manufacturer" to android.os.Build.MANUFACTURER,
                        "sdk" to android.os.Build.VERSION.SDK_INT,
                        "androidVersion" to android.os.Build.VERSION.RELEASE
                    )
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

    private suspend fun sendHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        val deviceId = DeviceManager.getOrCreateDeviceId(ctx.appContext)
        val apiKey   = DeviceManager.getApiKey(ctx.appContext)
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "Heartbeat skipped — not enrolled")
            return@withContext false
        }

        return@withContext try {
            val api = com.personal.agent.core.network.NetworkClient.forDevice(
                baseUrl  = com.personal.agent.BuildConfig.SERVER_BASE_URL,
                apiKey   = apiKey,
                deviceId = deviceId
            )

            // Collect config version from Room
            configVersion = ctx.db.configDao()
                .findByKey("version")
                ?.version ?: 0

            val jobQueueDepth = ctx.db.jobDao().countQueued()

            val payload = DeviceManager.buildHeartbeatPayload(
                context        = ctx.appContext,
                deviceId       = deviceId,
                configVersion  = configVersion,
                jobQueueDepth  = jobQueueDepth
            )
            val response = api.sendHeartbeat(payload)

            if (response.ok) {
                lastHeartbeatAt = System.currentTimeMillis()
                lastErrorCode   = null
                ctx.logger.info(
                    module  = id,
                    event   = "heartbeat_ok",
                    message = "Heartbeat sent successfully"
                )
                true
            } else {
                lastErrorCode = "server_rejected"
                ctx.logger.warn(
                    module  = id,
                    event   = "heartbeat_nok",
                    message = "Server returned ok=false"
                )
                false
            }
        } catch (e: Exception) {
            lastErrorCode = e.javaClass.simpleName
            ctx.logger.error(
                module  = id,
                event   = "heartbeat_error",
                message = e.message ?: "unknown error"
            )
            false
        }
    }

    companion object {
        const val MODULE_ID = "device_manager"
    }
}
