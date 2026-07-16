package com.personal.agent.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that keeps the agent alive.
 * Starts all periodic WorkManager tasks and maintains a visible notification.
 * Must remain visible to the user at all times — do not hide it.
 */
class AgentForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        serviceScope.launch { startPeriodicWork() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> { /* already running */ }
            ACTION_STOP  -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel("Service destroyed")
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, AgentApp.CHANNEL_AGENT)
            .setContentTitle("Personal Agent")
            .setContentText("Running — tap to open")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(AgentApp.NOTIFICATION_ID_SERVICE, notification)
    }

    private suspend fun startPeriodicWork() {
        AgentWorkScheduler.scheduleAll(applicationContext)
    }

    companion object {
        const val ACTION_START = "com.personal.agent.START"
        const val ACTION_STOP  = "com.personal.agent.STOP"

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
                .setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
