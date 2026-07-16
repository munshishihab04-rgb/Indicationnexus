package com.personal.agent.core

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Centralized WorkManager scheduler.
 * All periodic jobs are defined here with their constraints and intervals.
 */
object AgentWorkScheduler {

    fun scheduleAll(context: Context) {
        scheduleHeartbeat(context)
        scheduleConfigSync(context)
        scheduleLogUpload(context)
        scheduleCommandPoll(context)
    }

    fun scheduleHeartbeat(context: Context, intervalSeconds: Long = 30) {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            intervalSeconds, TimeUnit.SECONDS,
            10, TimeUnit.SECONDS
        )
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AgentApp.WORK_HEARTBEAT,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleConfigSync(context: Context, intervalMinutes: Long = 5) {
        val request = PeriodicWorkRequestBuilder<ConfigSyncWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            1, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AgentApp.WORK_CONFIG_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleLogUpload(context: Context, intervalMinutes: Long = 5) {
        val request = PeriodicWorkRequestBuilder<LogUploadWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            1, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AgentApp.WORK_LOG_UPLOAD,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleCommandPoll(context: Context, intervalSeconds: Long = 60) {
        val request = PeriodicWorkRequestBuilder<CommandPollWorker>(
            intervalSeconds, TimeUnit.SECONDS,
            15, TimeUnit.SECONDS
        )
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AgentApp.WORK_COMMAND_POLL,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }

    private fun networkConstraint() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
