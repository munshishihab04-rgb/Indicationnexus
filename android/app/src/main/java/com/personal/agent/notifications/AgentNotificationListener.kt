package com.personal.agent.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.personal.agent.core.db.AgentDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "NotificationListener"

/**
 * Phase 4 — Notification Engine listener.
 *
 * Enabled by user: Android Settings → Notifications → Notification access.
 * Lifecycle is managed by Android — NOT by the foreground service.
 *
 * Flow:
 *   onNotificationPosted → NotificationParser.parse → NotificationQueue.enqueue
 *
 * Upload is handled separately by [NotificationUploadWorker] (WorkManager).
 * This listener only persists — it never blocks the notification thread.
 */
class AgentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val parser by lazy { NotificationParser(packageManager) }
    private val queue  by lazy { NotificationQueue(AgentDatabase.getInstance(applicationContext)) }

    // ─── Listener lifecycle ───────────────────────────────────────────────────

    override fun onListenerConnected() {
        Log.i(TAG, "Notification listener connected")
        NotificationEngineState.connected = true
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Notification listener disconnected")
        NotificationEngineState.connected = false
    }

    // ─── Notification events ──────────────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!NotificationEngineState.enabled) return

        scope.launch {
            try {
                val event = parser.parse(sbn) ?: return@launch
                val inserted = queue.enqueue(event)
                if (inserted) {
                    Log.d(TAG, "Stored: [${event.source}] ${event.appName}: ${event.title?.take(50)}")
                    NotificationEngineState.received++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification from ${sbn.packageName}: ${e.message}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Not currently tracked — placeholder for Phase 7 automation triggers
    }
}

/**
 * Shared state between [AgentNotificationListener] and [NotificationEngine].
 * The listener is Android-managed (not started by the service), so state
 * must be passed via a static/singleton bridge.
 */
object NotificationEngineState {
    @Volatile var enabled: Boolean   = false
    @Volatile var connected: Boolean = false
    @Volatile var received: Long     = 0L
}
