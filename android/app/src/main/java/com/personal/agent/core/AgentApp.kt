package com.personal.agent.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class AgentApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Persistent agent channel (low importance — visible but not intrusive)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_AGENT,
                    "Agent Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Personal agent running status"
                    setShowBadge(false)
                }
            )

            // Alerts channel for errors and important events
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERT,
                    "Agent Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Agent errors and important events"
                }
            )
        }
    }

    companion object {
        const val CHANNEL_AGENT = "agent_service"
        const val CHANNEL_ALERT = "agent_alert"
        const val NOTIFICATION_ID_SERVICE = 1001

        // WorkManager unique work names
        const val WORK_HEARTBEAT       = "agent_heartbeat"
        const val WORK_CONFIG_SYNC     = "agent_config_sync"
        const val WORK_LOG_UPLOAD      = "agent_log_upload"
        const val WORK_COMMAND_POLL    = "agent_command_poll"
        const val WORK_BOOT_RECOVERY   = "agent_boot_recovery"
        const val WORK_CLEANUP         = "agent_cleanup"
    }
}
