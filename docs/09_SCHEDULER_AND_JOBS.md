# 09 — Scheduler and Jobs

## Purpose

The scheduler runs periodic and one-time tasks reliably across app restarts and device reboots.

## APIs

- WorkManager for deferrable/retriable work.
- AlarmManager for exact timing only when justified and allowed.
- Foreground service for active visible work.

## Job Types

- heartbeat;
- config sync;
- log upload;
- command poll;
- notification upload;
- OCR processing;
- vision upload;
- file upload/download;
- workflow step retry;
- database cleanup;
- health check.

## Job Table

Room table: `jobs`

| Column | Type |
|---|---|
| `id` | String primary key |
| `type` | String |
| `payload_json` | String |
| `status` | queued/running/succeeded/failed/cancelled |
| `priority` | Integer |
| `attempt` | Integer |
| `max_attempts` | Integer |
| `not_before` | Long |
| `created_at` | Long |
| `updated_at` | Long |
| `last_error` | String nullable |

## Retry Policy

```json
{
  "maxAttempts": 5,
  "baseDelayMs": 1000,
  "maxDelayMs": 300000,
  "multiplier": 2.0,
  "jitter": true
}
```

## Priority

Suggested priority levels:

| Priority | Meaning |
|---:|---|
| 100 | Command ACK, critical config/security |
| 80 | Heartbeat/status |
| 60 | User-triggered automation |
| 40 | Upload logs/events |
| 20 | Background sync |
| 10 | Cleanup |

## Worker Pattern

```kotlin
class AgentWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getString("jobId") ?: return Result.failure()
        return jobExecutor.execute(jobId).toWorkResult()
    }
}
```

## Reboot Recovery

On boot:

1. Start minimal receiver.
2. Enqueue recovery worker.
3. Worker loads config and job queue.
4. Restart enabled periodic work.
5. Do not perform heavy work directly inside receiver.
