package com.personal.agent.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personal.agent.core.AgentSession
import com.personal.agent.core.network.NotificationPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "NotificationUpload"
private const val WORK_NAME = "agent_notification_upload"

/**
 * Uploads pending notifications to POST /v1/notification.
 * Runs every 5 minutes while connected. Marks each row uploaded on success.
 */
class NotificationUploadWorker(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = AgentSession.get(applicationContext) ?: return@withContext Result.success()

        return@withContext try {
            val queue = NotificationQueue(session.db)
            val pending = queue.pendingUpload(limit = 50)

            if (pending.isEmpty()) {
                Log.d(TAG, "No pending notifications to upload")
                return@withContext Result.success()
            }

            Log.i(TAG, "Uploading ${pending.size} notifications")
            var uploaded = 0

            for (entity in pending) {
                try {
                    val payload = NotificationPayload(
                        id           = entity.id,
                        packageName  = entity.packageName,
                        appName      = entity.appName,
                        title        = entity.title,
                        body         = entity.body,
                        sender       = entity.sender,
                        conversation = entity.conversation,
                        timestamp    = entity.timestamp
                    )
                    val response = session.api.uploadNotification(payload)
                    if (response.ok) {
                        queue.markUploaded(entity.id)
                        uploaded++
                    } else {
                        Log.w(TAG, "Server rejected notification ${entity.id}: ok=false")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload notification ${entity.id}: ${e.message}")
                    // Continue with next — don't fail the whole batch
                }
            }

            Log.i(TAG, "Notification upload complete: $uploaded/${pending.size} uploaded")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "NotificationUploadWorker failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context, intervalMinutes: Long = 5) {
            val request = PeriodicWorkRequestBuilder<NotificationUploadWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                1, TimeUnit.MINUTES
            )
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.i(TAG, "NotificationUploadWorker scheduled every ${intervalMinutes}m")
        }
    }
}
