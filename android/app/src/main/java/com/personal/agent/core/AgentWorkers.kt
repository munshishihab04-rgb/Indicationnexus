package com.personal.agent.core

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

private const val TAG = "AgentWorker"

/**
 * Sends heartbeat to server with device health and module status.
 */
class HeartbeatWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "HeartbeatWorker: starting")
            // TODO Phase 2: inject DeviceManager, build and send heartbeat payload
            // val result = deviceManager.sendHeartbeat()
            Log.d(TAG, "HeartbeatWorker: success")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "HeartbeatWorker failed: ${e.message}")
            Result.retry()
        }
    }
}

/**
 * Fetches remote config and caches it locally.
 */
class ConfigSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "ConfigSyncWorker: starting")
            // TODO Phase 2: fetch /v1/config, save to Room, apply to ModuleRegistry
            Log.d(TAG, "ConfigSyncWorker: success")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ConfigSyncWorker failed: ${e.message}")
            Result.retry()
        }
    }
}

/**
 * Uploads pending log entries to server in batches.
 */
class LogUploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "LogUploadWorker: starting")
            // TODO Phase 2: load pending logs from Room, POST to /v1/logs, mark uploaded
            Log.d(TAG, "LogUploadWorker: success")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "LogUploadWorker failed: ${e.message}")
            Result.retry()
        }
    }
}

/**
 * Polls server for pending commands and dispatches them to ModuleRegistry.
 */
class CommandPollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "CommandPollWorker: starting")
            // TODO Phase 3: GET /v1/commands, save to Room, dispatch, POST /v1/ack
            Log.d(TAG, "CommandPollWorker: success")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CommandPollWorker failed: ${e.message}")
            Result.retry()
        }
    }
}
