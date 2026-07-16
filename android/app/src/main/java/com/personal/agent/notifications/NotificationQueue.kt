package com.personal.agent.notifications

import android.util.Log
import com.personal.agent.core.db.AgentDatabase
import com.personal.agent.core.db.NotificationEntity

private const val TAG = "NotificationQueue"

/**
 * Persists [NotificationEvent] objects to the Room `notifications` table.
 *
 * Responsibilities:
 * - Deduplicate by deterministic ID before inserting.
 * - Persist before any upload attempt — events survive app restarts.
 * - Provide pending events for the upload worker.
 * - Mark events as uploaded after server confirmation.
 */
class NotificationQueue(private val db: AgentDatabase) {

    /**
     * Persists the event if not already stored (dedup by ID).
     * Returns true if inserted, false if duplicate.
     */
    suspend fun enqueue(event: NotificationEvent): Boolean {
        val existing = db.notificationDao().findById(event.id)
        if (existing != null) {
            Log.d(TAG, "Duplicate notification skipped: ${event.id}")
            return false
        }

        db.notificationDao().upsert(
            NotificationEntity(
                id           = event.id,
                packageName  = event.packageName,
                appName      = event.appName,
                title        = event.title,
                body         = event.body,
                sender       = event.sender,
                conversation = event.conversation,
                timestamp    = event.timestamp,
                uploadedAt   = null
            )
        )
        Log.d(TAG, "Enqueued notification ${event.packageName}: ${event.title?.take(40)}")
        return true
    }

    /**
     * Returns all notifications not yet uploaded, ordered oldest first.
     * Batch size capped at 50 to keep upload payloads manageable.
     */
    suspend fun pendingUpload(limit: Int = 50): List<NotificationEntity> =
        db.notificationDao().getPendingUpload().take(limit)

    /** Marks a notification as uploaded. */
    suspend fun markUploaded(id: String) {
        db.notificationDao().markUploaded(id, System.currentTimeMillis())
    }

    /** Returns the count of pending notifications. */
    suspend fun pendingCount(): Int =
        db.notificationDao().countPendingUpload()
}
