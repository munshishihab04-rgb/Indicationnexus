package com.personal.agent.core.queue

import android.content.Context
import android.util.Log
import com.personal.agent.core.db.AgentDatabase
import com.personal.agent.core.db.JobEntity
import com.personal.agent.core.modules.AgentCommand
import com.personal.agent.core.modules.ModuleRegistry
import kotlinx.coroutines.delay
import java.util.UUID

private const val TAG = "JobExecutor"

/**
 * Executes queued [JobEntity] rows from the Room `jobs` table.
 *
 * Execution model:
 *   1. Load all ready jobs ordered by priority DESC, created_at ASC.
 *   2. Mark each job as `running`.
 *   3. Route to the appropriate handler by job type.
 *   4. On success: mark `succeeded`.
 *   5. On failure: apply exponential backoff and mark `queued` (retry) or `failed` (exhausted).
 *
 * All retry/backoff logic lives here so workers just call [runPending] and get a clean result.
 */
class JobExecutor(
    private val context: Context,
    private val db: AgentDatabase,
    private val registry: ModuleRegistry?
) {

    /**
     * Runs all jobs that are ready (status=queued, not_before <= now).
     * Returns the number of jobs that succeeded.
     */
    suspend fun runPending(): Int {
        val now = System.currentTimeMillis()
        val jobs = db.jobDao().getQueuedReady(now)
        if (jobs.isEmpty()) return 0

        Log.i(TAG, "runPending: ${jobs.size} jobs ready")
        var succeeded = 0

        for (job in jobs) {
            val attempt = job.attempt + 1
            // Mark running
            db.jobDao().updateStatus(
                id = job.id,
                status = "running",
                attempt = attempt,
                lastError = null,
                updatedAt = System.currentTimeMillis()
            )

            try {
                val result = dispatch(job)
                if (result.success) {
                    db.jobDao().updateStatus(
                        id = job.id,
                        status = "succeeded",
                        attempt = attempt,
                        lastError = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    Log.i(TAG, "Job ${job.type}[${job.id}] succeeded on attempt $attempt")
                    succeeded++
                } else {
                    handleFailure(job, attempt, result.errorMessage ?: "handler returned failure")
                }
            } catch (e: Exception) {
                handleFailure(job, attempt, e.message ?: "exception")
            }
        }
        return succeeded
    }

    /**
     * Enqueues a new job with the given type, payload JSON, and priority.
     * Returns the job ID.
     */
    suspend fun enqueue(
        type: String,
        payloadJson: String = "{}",
        priority: Int = PRIORITY_NORMAL,
        maxAttempts: Int = 5,
        delayMs: Long = 0
    ): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.jobDao().upsert(
            JobEntity(
                id = id,
                type = type,
                payloadJson = payloadJson,
                status = "queued",
                priority = priority,
                attempt = 0,
                maxAttempts = maxAttempts,
                notBefore = now + delayMs,
                createdAt = now,
                updatedAt = now,
                lastError = null
            )
        )
        Log.d(TAG, "Enqueued job $type[$id] priority=$priority delay=${delayMs}ms")
        return id
    }

    // ─── Routing ──────────────────────────────────────────────────────────────

    private suspend fun dispatch(job: JobEntity): JobResult {
        Log.d(TAG, "Dispatching ${job.type}[${job.id}] attempt=${job.attempt + 1}")

        return when (job.type) {
            // Core job types handled internally
            JOB_TYPE_HEARTBEAT    -> JobResult.success()
            JOB_TYPE_CONFIG_SYNC  -> JobResult.success()
            JOB_TYPE_LOG_UPLOAD   -> JobResult.success()
            JOB_TYPE_CLEANUP      -> {
                runCleanup()
                JobResult.success()
            }

            // Command-driven jobs: route payload as AgentCommand to ModuleRegistry
            else -> {
                val reg = registry
                if (reg == null) {
                    Log.w(TAG, "ModuleRegistry not available — cannot dispatch ${job.type}")
                    return JobResult.failure("ModuleRegistry not initialized")
                }
                val command = AgentCommand(
                    id = job.id,
                    type = job.type,
                    payload = parsePayload(job.payloadJson)
                )
                val result = reg.dispatchCommand(command)
                if (result.success) {
                    JobResult.success()
                } else {
                    JobResult.failure(result.errorCode ?: "module_error")
                }
            }
        }
    }

    // ─── Retention cleanup ────────────────────────────────────────────────────

    private suspend fun runCleanup() {
        val cutoff14d = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
        val cutoff7d  = System.currentTimeMillis() -  7L * 24 * 60 * 60 * 1000
        val cutoff30d = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000

        db.logDao().deleteOlderThan(cutoff14d)
        db.commandDao().deleteCompletedOlderThan(cutoff30d)
        db.jobDao().deleteCompletedOlderThan(cutoff30d)
        db.notificationDao().deleteOlderThan(cutoff7d)
        db.screenDao().deleteOlderThan(cutoff7d)
        db.ocrDao().deleteOlderThan(cutoff7d)
        db.visionDao().deleteOlderThan(cutoff7d)
        Log.i(TAG, "Cleanup complete")
    }

    // ─── Retry / backoff ──────────────────────────────────────────────────────

    private suspend fun handleFailure(job: JobEntity, attempt: Int, error: String) {
        if (attempt >= job.maxAttempts) {
            db.jobDao().updateStatus(
                id = job.id,
                status = "failed",
                attempt = attempt,
                lastError = error.take(500),
                updatedAt = System.currentTimeMillis()
            )
            Log.e(TAG, "Job ${job.type}[${job.id}] failed permanently after $attempt attempts: $error")
        } else {
            val backoffMs = backoffDelay(attempt)
            db.jobDao().updateStatus(
                id = job.id,
                status = "queued",
                attempt = attempt,
                lastError = error.take(500),
                updatedAt = System.currentTimeMillis()
            )
            // Update notBefore to implement backoff — re-upsert with new notBefore
            val updated = db.jobDao().findById(job.id)
            if (updated != null) {
                db.jobDao().upsert(
                    updated.copy(notBefore = System.currentTimeMillis() + backoffMs)
                )
            }
            Log.w(TAG, "Job ${job.type}[${job.id}] retry in ${backoffMs}ms (attempt $attempt/${job.maxAttempts}): $error")
        }
    }

    /**
     * Exponential backoff: 1s, 2s, 4s, 8s, … capped at 5 minutes, with ±10% jitter.
     */
    private fun backoffDelay(attempt: Int): Long {
        val base = BASE_DELAY_MS * (1L shl minOf(attempt - 1, 8))
        val capped = minOf(base, MAX_DELAY_MS)
        val jitter = (capped * 0.1 * (Math.random() * 2 - 1)).toLong()
        return (capped + jitter).coerceAtLeast(BASE_DELAY_MS)
    }

    private fun parsePayload(json: String): Map<String, Any> {
        // Simple parser for flat string/number/boolean values
        return try {
            val map = mutableMapOf<String, Any>()
            val trimmed = json.trim().removePrefix("{").removeSuffix("}")
            if (trimmed.isBlank()) return map
            trimmed.split(",").forEach { pair ->
                val kv = pair.trim().split(":", limit = 2)
                if (kv.size == 2) {
                    val k = kv[0].trim().trim('"')
                    val v = kv[1].trim().trim('"')
                    map[k] = when {
                        v == "true"  -> true
                        v == "false" -> false
                        v.toLongOrNull() != null -> v.toLong()
                        else -> v
                    }
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    companion object {
        const val PRIORITY_CRITICAL = 100
        const val PRIORITY_HIGH     = 80
        const val PRIORITY_NORMAL   = 60
        const val PRIORITY_LOW      = 40
        const val PRIORITY_BG       = 20
        const val PRIORITY_CLEANUP  = 10

        const val JOB_TYPE_HEARTBEAT   = "heartbeat"
        const val JOB_TYPE_CONFIG_SYNC = "config_sync"
        const val JOB_TYPE_LOG_UPLOAD  = "log_upload"
        const val JOB_TYPE_CLEANUP     = "cleanup"

        private const val BASE_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS  = 5L * 60 * 1_000L
    }
}

data class JobResult(
    val success: Boolean,
    val errorMessage: String? = null
) {
    companion object {
        fun success() = JobResult(true)
        fun failure(msg: String) = JobResult(false, msg)
    }
}
