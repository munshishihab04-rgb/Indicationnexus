package com.personal.agent.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personal.agent.core.db.AgentDatabase
import com.personal.agent.core.logging.AgentLogger
import com.personal.agent.core.modules.ModuleContext
import com.personal.agent.core.modules.ModuleRegistry
import com.personal.agent.device.DeviceManagerModule
import com.personal.agent.notifications.NotificationEngine
import com.personal.agent.permissions.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "AgentForegroundService"

/**
 * Persistent foreground service that keeps the agent alive.
 *
 * Phase 3: initializes [ModuleRegistry] with all registered modules,
 * starts enabled modules based on remote config, and schedules all
 * periodic WorkManager workers.
 *
 * Must remain visible to the user at all times — do not hide the notification.
 */
class AgentForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Singleton registry — accessible by CommandPollWorker via companion. */
    private var registry: ModuleRegistry? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        serviceScope.launch { initialize() }
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
        Log.i(TAG, "Service destroyed — cancelling coroutines")
        serviceScope.cancel("Service destroyed")
        _registry = null
        super.onDestroy()
    }

    // ─── Initialization ───────────────────────────────────────────────────────

    private suspend fun initialize() {
        val db     = AgentDatabase.getInstance(applicationContext)

        // AgentLogger uses its own internal LogDao (from core/logging).
        // We bridge by wrapping db.logDao() — both share the same 'logs' table.
        val loggerDao = object : com.personal.agent.core.logging.LogDao {
            override suspend fun insert(entry: com.personal.agent.core.logging.LogEntry) {
                db.logDao().upsert(
                    com.personal.agent.core.db.LogEntity(
                        id = entry.id,
                        level = entry.level,
                        module = entry.module,
                        event = entry.event,
                        message = entry.message,
                        dataJson = entry.dataJson,
                        createdAt = entry.createdAt,
                        uploadedAt = entry.uploadedAt
                    )
                )
            }
            override suspend fun getPendingUploads(): List<com.personal.agent.core.logging.LogEntry> =
                db.logDao().getPendingUpload(200).map { e ->
                    com.personal.agent.core.logging.LogEntry(
                        id = e.id, level = e.level, module = e.module,
                        event = e.event, message = e.message ?: "",
                        dataJson = e.dataJson, createdAt = e.createdAt, uploadedAt = e.uploadedAt
                    )
                }
            override suspend fun markUploaded(ids: List<String>, uploadedAt: Long) =
                db.logDao().markUploaded(ids, uploadedAt)
            override suspend fun pruneUploaded(olderThanMs: Long) =
                db.logDao().deleteOlderThan(olderThanMs)
            override suspend fun count(): Int = db.logDao().countPendingUpload()
        }
        val logger = AgentLogger(loggerDao, serviceScope)

        val moduleContext = ModuleContext(
            appContext   = applicationContext,
            db           = db,
            logger       = logger,
            configStore  = null,   // Phase 4: wire RemoteConfigDao once merged into AgentDatabase
            scope        = serviceScope
        )

        // Register all available modules
        val modules = setOf(
            DeviceManagerModule(),
            PermissionManager(),
            NotificationEngine()
        )

        val reg = ModuleRegistry(modules, moduleContext)
        registry = reg
        _registry = reg

        logger.info(TAG, "service_started", "Agent foreground service initialized")

        // Start modules that are enabled in remote config
        reg.startEnabledModules()

        // Schedule all periodic WorkManager workers
        AgentWorkScheduler.scheduleAll(applicationContext)

        Log.i(TAG, "Initialization complete")
    }

    // ─── Notification ─────────────────────────────────────────────────────────

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

    companion object {
        const val ACTION_START = "com.personal.agent.START"
        const val ACTION_STOP  = "com.personal.agent.STOP"

        /** Global registry reference for workers that need to dispatch commands. */
        @Volatile
        var _registry: ModuleRegistry? = null

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, AgentForegroundService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AgentForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
