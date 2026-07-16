package com.personal.agent.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.agent.core.AgentApp
import com.personal.agent.core.AgentForegroundService
import com.personal.agent.core.AgentWorkScheduler
import androidx.work.*

/**
 * Receives BOOT_COMPLETED and restores all periodic work.
 * Does NOT perform heavy work directly — enqueues a recovery worker.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "Boot received — enqueuing recovery")

        val request = OneTimeWorkRequestBuilder<BootRecoveryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            AgentApp.WORK_BOOT_RECOVERY,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}

/**
 * Runs after boot: restarts the foreground service and all periodic workers.
 */
class BootRecoveryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Log.i(TAG, "BootRecovery: starting agent services")
        try {
            AgentForegroundService.start(applicationContext)
            AgentWorkScheduler.scheduleAll(applicationContext)
            Log.i(TAG, "BootRecovery: complete")
        } catch (e: Exception) {
            Log.e(TAG, "BootRecovery failed: ${e.message}")
            return Result.retry()
        }
        return Result.success()
    }

    companion object { private const val TAG = "BootRecovery" }
}
