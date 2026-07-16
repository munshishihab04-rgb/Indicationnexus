package com.personal.agent.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Notification Engine — Phase 4.
 * Enabled by user in Android Settings > Notifications > Notification access.
 * Receives all status bar notifications and parses/queues them.
 *
 * Implementation: Phase 4 — not yet active.
 */
class AgentNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        Log.d(TAG, "Notification posted: ${sbn.packageName}")
        // Phase 4: parse, normalize, deduplicate, save to Room, enqueue upload
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    companion object { private const val TAG = "NotificationListener" }
}
