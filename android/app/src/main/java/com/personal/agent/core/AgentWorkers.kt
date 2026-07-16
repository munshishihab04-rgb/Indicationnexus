package com.personal.agent.core

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.agent.BuildConfig
import com.personal.agent.core.db.AgentDatabase
import com.personal.agent.core.db.CommandEntity
import com.personal.agent.core.db.ConfigEntity
import com.personal.agent.core.network.AckRequest
import com.personal.agent.core.network.LogPayload
import com.personal.agent.core.network.LogUploadRequest
import com.personal.agent.device.DeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AgentWorker"

// ─── HeartbeatWorker ──────────────────────────────────────────────────────────

/**
 * Sends device health snapshot to POST /v1/heartbeat.
 * Reads live battery, network, storage, memory state via DeviceManager.
 * Skips silently if device is not yet enrolled.
 */
class HeartbeatWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = AgentSession.get(applicationContext) ?: return@withContext Result.success()
        return@withContext try {
            val db = session.db
            val configVersion = db.configDao()
                .findByKey(CONFIG_KEY_VERSION)
                ?.version ?: 0
            val jobQueueDepth = db.jobDao().countQueued()

            val payload = DeviceManager.buildHeartbeatPayload(
                context = applicationContext,
                deviceId = session.deviceId,
                moduleStatuses = emptyList(), // Phase 3+: collect from ModuleRegistry
                configVersion = configVersion,
                jobQueueDepth = jobQueueDepth
            )
            val response = session.api.sendHeartbeat(payload)
            if (response.ok) {
                Log.d(TAG, "Heartbeat OK")
                Result.success()
            } else {
                Log.w(TAG, "Heartbeat NOK: ok=${response.ok}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "HeartbeatWorker failed: ${e.message}")
            Result.retry()
        }
    }
}

// ─── ConfigSyncWorker ─────────────────────────────────────────────────────────

/**
 * Fetches remote config from GET /v1/config and caches it in Room.
 * Applies new intervals to WorkManager schedules.
 * No-ops if config version has not changed.
 */
class ConfigSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = AgentSession.get(applicationContext) ?: return@withContext Result.success()
        return@withContext try {
            val db = session.db
            val currentVersion = db.configDao()
                .findByKey(CONFIG_KEY_VERSION)
                ?.version ?: 0

            val response = session.api.getConfig(
                deviceId = session.deviceId,
                currentVersion = currentVersion
            )

            if (!response.ok) {
                Log.w(TAG, "ConfigSync response not ok")
                return@withContext Result.retry()
            }

            val configData = response.data
            if (configData?.config == null) {
                Log.d(TAG, "ConfigSync: no update (version=$currentVersion is current)")
                return@withContext Result.success()
            }

            val newConfig = configData.config
            val now = System.currentTimeMillis()

            // Persist each config section as a separate keyed row
            val entries = listOf(
                ConfigEntity(CONFIG_KEY_VERSION, configData.version.toString(), configData.version, now),
                ConfigEntity(CONFIG_KEY_MODULES, serializeModules(newConfig.modules), configData.version, now),
                ConfigEntity(CONFIG_KEY_INTERVALS, serializeIntervals(newConfig.intervals), configData.version, now),
                ConfigEntity(CONFIG_KEY_LIMITS, serializeLimits(newConfig.limits), configData.version, now),
                ConfigEntity(CONFIG_KEY_PRIVACY, serializePrivacy(newConfig.privacy), configData.version, now),
                ConfigEntity(CONFIG_KEY_APPS, serializeApps(newConfig.apps), configData.version, now),
            )
            db.configDao().upsertAll(entries)

            // Re-schedule workers with updated intervals
            val intervals = newConfig.intervals
            AgentWorkScheduler.scheduleHeartbeat(
                applicationContext,
                intervals.heartbeatSeconds.toLong().coerceAtLeast(15)
            )
            AgentWorkScheduler.scheduleConfigSync(
                applicationContext,
                intervals.configSyncMinutes.toLong().coerceAtLeast(1)
            )
            AgentWorkScheduler.scheduleLogUpload(
                applicationContext,
                intervals.logUploadMinutes.toLong().coerceAtLeast(1)
            )

            Log.i(TAG, "ConfigSync: updated to version ${configData.version}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ConfigSyncWorker failed: ${e.message}")
            Result.retry()
        }
    }

    // Minimal JSON serializers — avoids Moshi dependency in worker
    private fun serializeModules(modules: Map<String, com.personal.agent.core.network.ModuleConfigPayload>): String {
        val sb = StringBuilder("{")
        modules.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            sb.append("\"$k\":{\"enabled\":${v.enabled}}")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun serializeIntervals(i: com.personal.agent.core.network.IntervalsPayload) =
        """{"heartbeatSeconds":${i.heartbeatSeconds},"configSyncMinutes":${i.configSyncMinutes},"logUploadMinutes":${i.logUploadMinutes}}"""

    private fun serializeLimits(l: com.personal.agent.core.network.LimitsPayload) =
        """{"actionsPerMinute":${l.actionsPerMinute},"maxWorkflowSteps":${l.maxWorkflowSteps},"maxQueueSize":${l.maxQueueSize}}"""

    private fun serializePrivacy(p: com.personal.agent.core.network.PrivacyPayload): String {
        val patterns = p.redactPatterns.joinToString(",") { "\"$it\"" }
        return """{"storeScreenshots":${p.storeScreenshots},"uploadScreenshots":${p.uploadScreenshots},"redactPatterns":[$patterns]}"""
    }

    private fun serializeApps(a: com.personal.agent.core.network.AppsPayload): String {
        val allow = a.allowlist.joinToString(",") { "\"$it\"" }
        val deny = a.denylist.joinToString(",") { "\"$it\"" }
        return """{"allowlist":[$allow],"denylist":[$deny]}"""
    }
}

// ─── LogUploadWorker ──────────────────────────────────────────────────────────

/**
 * Uploads pending log rows to POST /v1/logs in batches of 100.
 * Marks rows as uploaded on success. Skips rows that fail to avoid infinite retry.
 */
class LogUploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = AgentSession.get(applicationContext) ?: return@withContext Result.success()
        return@withContext try {
            val db = session.db
            val pending = db.logDao().getPendingUpload(batchSize = 100)
            if (pending.isEmpty()) {
                Log.d(TAG, "LogUpload: nothing to upload")
                return@withContext Result.success()
            }

            val payloads = pending.map { entry ->
                LogPayload(
                    id = entry.id,
                    level = entry.level,
                    module = entry.module,
                    event = entry.event,
                    message = entry.message,
                    data = null, // dataJson not deserialized to avoid Moshi overhead here
                    createdAt = entry.createdAt
                )
            }

            val response = session.api.uploadLogs(
                LogUploadRequest(deviceId = session.deviceId, logs = payloads)
            )

            if (response.ok) {
                val uploadedAt = System.currentTimeMillis()
                db.logDao().markUploaded(
                    ids = pending.map { it.id },
                    uploadedAt = uploadedAt
                )
                Log.i(TAG, "LogUpload: uploaded ${pending.size} entries")
                Result.success()
            } else {
                Log.w(TAG, "LogUpload: server returned ok=false")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "LogUploadWorker failed: ${e.message}")
            Result.retry()
        }
    }
}

// ─── CommandPollWorker ────────────────────────────────────────────────────────

/**
 * Polls GET /v1/commands, persists new commands to Room, dispatches them,
 * then ACKs each one via POST /v1/ack.
 *
 * ACK is sent regardless of dispatch success — the result field carries the outcome.
 */
class CommandPollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = AgentSession.get(applicationContext) ?: return@withContext Result.success()
        return@withContext try {
            val db = session.db

            // 1. Fetch pending commands from server
            val response = session.api.getCommands(limit = 20)
            if (!response.ok || response.data == null) {
                Log.w(TAG, "CommandPoll: server returned ok=${response.ok}")
                return@withContext Result.retry()
            }

            val commands = response.data.commands
            if (commands.isEmpty()) {
                Log.d(TAG, "CommandPoll: no pending commands")
                return@withContext Result.success()
            }

            Log.i(TAG, "CommandPoll: received ${commands.size} commands")

            // 2. Persist + dispatch each command
            for (cmd in commands) {
                // Skip if already stored (idempotent delivery)
                val existing = db.commandDao().findById(cmd.id)
                if (existing != null && existing.status != "pending") {
                    Log.d(TAG, "CommandPoll: skipping already-processed command ${cmd.id}")
                    continue
                }

                // Persist as pending
                db.commandDao().upsert(
                    CommandEntity(
                        id = cmd.id,
                        type = cmd.type,
                        payloadJson = serializePayload(cmd.payload),
                        status = "pending",
                        receivedAt = System.currentTimeMillis(),
                        ackedAt = null,
                        resultJson = null
                    )
                )

                // Dispatch (routes to ModuleRegistry if available, otherwise marks acked)
                val registry = AgentForegroundService._registry
                val (status, resultJson) = try {
                    Log.d(TAG, "CommandPoll: dispatching ${cmd.type} [${cmd.id}]")
                    if (registry != null) {
                        val agentCmd = com.personal.agent.core.modules.AgentCommand(
                            id = cmd.id,
                            type = cmd.type,
                            payload = cmd.payload
                        )
                        val cmdResult = registry.dispatchCommand(agentCmd)
                        if (cmdResult.success) {
                            "acked" to """{"dispatched":true,"type":"${cmd.type}"}"""
                        } else {
                            "failed" to """{"error":"${cmdResult.errorCode}"}"""
                        }
                    } else {
                        // Registry not yet initialized — accept command anyway
                        "acked" to """{"dispatched":false,"reason":"registry_not_ready"}"""
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "CommandPoll: dispatch failed for ${cmd.id}: ${e.message}")
                    "failed" to """{"error":"${e.message?.take(200)}"}"""
                }

                // ACK
                try {
                    val ackResp = session.api.ackCommand(
                        AckRequest(
                            commandId = cmd.id,
                            status = status,
                            result = null,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    val finalStatus = if (ackResp.ok) status else "failed"
                    db.commandDao().updateStatus(
                        id = cmd.id,
                        status = finalStatus,
                        ackedAt = if (ackResp.ok) System.currentTimeMillis() else null,
                        resultJson = resultJson
                    )
                    Log.d(TAG, "CommandPoll: ACKed ${cmd.id} → $finalStatus")
                } catch (e: Exception) {
                    Log.e(TAG, "CommandPoll: ACK failed for ${cmd.id}: ${e.message}")
                    db.commandDao().updateStatus(
                        id = cmd.id,
                        status = "failed",
                        ackedAt = null,
                        resultJson = "{\"ack_error\":\"" + (e.message?.take(200) ?: "unknown") + "\"}"
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CommandPollWorker failed: ${e.message}")
            Result.retry()
        }
    }

    private fun serializePayload(payload: Map<String, Any>): String {
        val sb = StringBuilder("{")
        payload.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append(",")
            val vStr = when (v) {
                is String  -> "\"$v\""
                is Boolean -> v.toString()
                is Number  -> v.toString()
                else       -> "\"${v.toString().replace("\"", "\\\"")}\""
            }
            sb.append("\"$k\":$vStr")
        }
        sb.append("}")
        return sb.toString()
    }
}

// ─── Shared constants ─────────────────────────────────────────────────────────

internal const val CONFIG_KEY_VERSION   = "version"
internal const val CONFIG_KEY_MODULES   = "modules"
internal const val CONFIG_KEY_INTERVALS = "intervals"
internal const val CONFIG_KEY_LIMITS    = "limits"
internal const val CONFIG_KEY_PRIVACY   = "privacy"
internal const val CONFIG_KEY_APPS      = "apps"

// ─── CleanupWorker ────────────────────────────────────────────────────────────

/**
 * Runs daily to purge old data from Room based on retention policy.
 * Does not require network — runs on any connection state.
 */
class CleanupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val db = AgentDatabase.getInstance(applicationContext)
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000

            db.logDao().deleteOlderThan(now - 14 * day)
            db.commandDao().deleteCompletedOlderThan(now - 30 * day)
            db.jobDao().deleteCompletedOlderThan(now - 30 * day)
            db.notificationDao().deleteOlderThan(now - 7 * day)
            db.screenDao().deleteOlderThan(now - 1 * day)
            db.ocrDao().deleteOlderThan(now - 3 * day)
            db.visionDao().deleteOlderThan(now - 3 * day)

            Log.i(TAG, "CleanupWorker: retention cleanup complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CleanupWorker failed: ${e.message}")
            Result.retry()
        }
    }
}
