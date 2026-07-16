package com.personal.agent.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// ---------------------------------------------------------------------------
// DeviceDao
// ---------------------------------------------------------------------------

@Dao
interface DeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceEntity)

    @Query("SELECT * FROM device WHERE device_id = :deviceId LIMIT 1")
    suspend fun findById(deviceId: String): DeviceEntity?

    @Query("SELECT * FROM device")
    suspend fun getAll(): List<DeviceEntity>

    @Query("UPDATE device SET enrolled = :enrolled, updated_at = :updatedAt WHERE device_id = :deviceId")
    suspend fun updateEnrolled(deviceId: String, enrolled: Boolean, updatedAt: Long)

    @Query("UPDATE device SET api_key_alias = :alias, updated_at = :updatedAt WHERE device_id = :deviceId")
    suspend fun updateApiKeyAlias(deviceId: String, alias: String?, updatedAt: Long)

    @Query("DELETE FROM device WHERE updated_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM device WHERE enrolled = 0")
    suspend fun countUnenrolled(): Int
}

// ---------------------------------------------------------------------------
// JobDao
// ---------------------------------------------------------------------------

@Dao
interface JobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: JobEntity)

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): JobEntity?

    /**
     * Returns all queued jobs whose not_before threshold has already passed,
     * ordered by priority (highest first) then creation time (oldest first).
     */
    @Query(
        """
        SELECT * FROM jobs
        WHERE status = 'queued'
          AND not_before <= :nowMs
        ORDER BY priority DESC, created_at ASC
        """
    )
    suspend fun getQueuedReady(nowMs: Long): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE status = 'running'")
    suspend fun getRunning(): List<JobEntity>

    @Query(
        """
        UPDATE jobs
        SET status = :status, attempt = :attempt, last_error = :lastError, updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        attempt: Int,
        lastError: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM jobs WHERE created_at < :beforeTimestamp AND status IN ('succeeded','failed','cancelled')")
    suspend fun deleteCompletedOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM jobs WHERE status = 'queued'")
    suspend fun countQueued(): Int

    @Query("SELECT COUNT(*) FROM jobs WHERE status = 'running'")
    suspend fun countRunning(): Int
}

// ---------------------------------------------------------------------------
// AutomationDao
// ---------------------------------------------------------------------------

@Dao
interface AutomationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AutomationEntity)

    @Query("SELECT * FROM automation WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AutomationEntity?

    @Query("SELECT * FROM automation WHERE enabled = 1 ORDER BY name ASC")
    suspend fun getEnabled(): List<AutomationEntity>

    @Query("SELECT * FROM automation ORDER BY name ASC")
    suspend fun getAll(): List<AutomationEntity>

    @Query("UPDATE automation SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM automation WHERE updated_at < :beforeTimestamp AND enabled = 0")
    suspend fun deleteDisabledOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM automation WHERE enabled = 1")
    suspend fun countEnabled(): Int
}

// ---------------------------------------------------------------------------
// AutomationRunDao
// ---------------------------------------------------------------------------

@Dao
interface AutomationRunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AutomationRunEntity)

    @Query("SELECT * FROM automation_runs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AutomationRunEntity?

    @Query("SELECT * FROM automation_runs WHERE status IN ('queued','running') ORDER BY started_at ASC")
    suspend fun getPending(): List<AutomationRunEntity>

    @Query("SELECT * FROM automation_runs WHERE workflow_id = :workflowId ORDER BY started_at DESC")
    suspend fun getByWorkflow(workflowId: String): List<AutomationRunEntity>

    @Query(
        """
        UPDATE automation_runs
        SET status = :status,
            current_step = :currentStep,
            attempts = :attempts,
            finished_at = :finishedAt,
            last_error = :lastError,
            result_summary = :resultSummary
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        currentStep: String?,
        attempts: Int,
        finishedAt: Long?,
        lastError: String?,
        resultSummary: String?
    )

    @Query("DELETE FROM automation_runs WHERE started_at < :beforeTimestamp AND status IN ('succeeded','failed','cancelled')")
    suspend fun deleteCompletedOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM automation_runs WHERE status IN ('queued','running')")
    suspend fun countPending(): Int
}

// ---------------------------------------------------------------------------
// NotificationDao
// ---------------------------------------------------------------------------

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): NotificationEntity?

    /** Notifications that have not yet been uploaded to the server. */
    @Query("SELECT * FROM notifications WHERE uploaded_at IS NULL ORDER BY timestamp ASC")
    suspend fun getPendingUpload(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE package_name = :packageName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByPackage(packageName: String, limit: Int = 50): List<NotificationEntity>

    @Query("UPDATE notifications SET uploaded_at = :uploadedAt WHERE id = :id")
    suspend fun markUploaded(id: String, uploadedAt: Long)

    @Query("DELETE FROM notifications WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM notifications WHERE uploaded_at IS NULL")
    suspend fun countPendingUpload(): Int
}

// ---------------------------------------------------------------------------
// ScreenDao
// ---------------------------------------------------------------------------

@Dao
interface ScreenDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScreenEntity)

    @Query("SELECT * FROM screens WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ScreenEntity?

    @Query("SELECT * FROM screens WHERE package_name = :packageName ORDER BY created_at DESC LIMIT :limit")
    suspend fun getByPackage(packageName: String, limit: Int = 20): List<ScreenEntity>

    /** Screens that have no associated OCR result yet (pending OCR processing). */
    @Query(
        """
        SELECT s.* FROM screens s
        LEFT JOIN ocr o ON o.screen_id = s.id
        WHERE o.id IS NULL
        ORDER BY s.created_at ASC
        """
    )
    suspend fun getPendingOcr(): List<ScreenEntity>

    @Query("DELETE FROM screens WHERE created_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM screens WHERE created_at >= :sinceTimestamp")
    suspend fun countSince(sinceTimestamp: Long): Int
}

// ---------------------------------------------------------------------------
// OcrDao
// ---------------------------------------------------------------------------

@Dao
interface OcrDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OcrEntity)

    @Query("SELECT * FROM ocr WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): OcrEntity?

    @Query("SELECT * FROM ocr WHERE screen_id = :screenId ORDER BY created_at ASC")
    suspend fun getByScreen(screenId: String): List<OcrEntity>

    /** OCR rows whose associated screen has no vision result yet. */
    @Query(
        """
        SELECT o.* FROM ocr o
        LEFT JOIN vision v ON v.screen_id = o.screen_id
        WHERE v.id IS NULL
        ORDER BY o.created_at ASC
        """
    )
    suspend fun getPendingVision(): List<OcrEntity>

    @Query("DELETE FROM ocr WHERE created_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM ocr WHERE created_at >= :sinceTimestamp")
    suspend fun countSince(sinceTimestamp: Long): Int
}

// ---------------------------------------------------------------------------
// VisionDao
// ---------------------------------------------------------------------------

@Dao
interface VisionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VisionEntity)

    @Query("SELECT * FROM vision WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): VisionEntity?

    @Query("SELECT * FROM vision WHERE screen_id = :screenId ORDER BY created_at DESC")
    suspend fun getByScreen(screenId: String): List<VisionEntity>

    /** Vision rows that reference screens without an accessibility tree (may need re-analysis). */
    @Query(
        """
        SELECT v.* FROM vision v
        INNER JOIN screens s ON s.id = v.screen_id
        WHERE s.accessibility_json IS NULL
        ORDER BY v.created_at ASC
        """
    )
    suspend fun getPendingAccessibility(): List<VisionEntity>

    @Query("DELETE FROM vision WHERE created_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM vision WHERE created_at >= :sinceTimestamp")
    suspend fun countSince(sinceTimestamp: Long): Int
}

// ---------------------------------------------------------------------------
// LogDao
// ---------------------------------------------------------------------------

@Dao
interface LogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LogEntity>)

    @Query("SELECT * FROM logs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): LogEntity?

    /** Log rows not yet uploaded to the remote sink. */
    @Query("SELECT * FROM logs WHERE uploaded_at IS NULL ORDER BY created_at ASC LIMIT :batchSize")
    suspend fun getPendingUpload(batchSize: Int = 200): List<LogEntity>

    @Query("SELECT * FROM logs WHERE level = :level ORDER BY created_at DESC LIMIT :limit")
    suspend fun getByLevel(level: String, limit: Int = 100): List<LogEntity>

    @Query("UPDATE logs SET uploaded_at = :uploadedAt WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<String>, uploadedAt: Long)

    @Query("DELETE FROM logs WHERE created_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM logs WHERE uploaded_at IS NULL")
    suspend fun countPendingUpload(): Int
}

// ---------------------------------------------------------------------------
// PermissionDao
// ---------------------------------------------------------------------------

@Dao
interface PermissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PermissionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PermissionEntity>)

    @Query("SELECT * FROM permissions WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): PermissionEntity?

    @Query("SELECT * FROM permissions")
    suspend fun getAll(): List<PermissionEntity>

    /** Permissions that are currently denied. */
    @Query("SELECT * FROM permissions WHERE granted = 0")
    suspend fun getDenied(): List<PermissionEntity>

    @Query("UPDATE permissions SET granted = :granted, last_checked_at = :lastCheckedAt WHERE `key` = :key")
    suspend fun updateGranted(key: String, granted: Boolean, lastCheckedAt: Long)

    @Query("UPDATE permissions SET last_prompted_at = :lastPromptedAt WHERE `key` = :key")
    suspend fun updateLastPrompted(key: String, lastPromptedAt: Long)

    @Query("DELETE FROM permissions WHERE last_checked_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM permissions WHERE granted = 0")
    suspend fun countDenied(): Int
}

// ---------------------------------------------------------------------------
// ConfigDao
// ---------------------------------------------------------------------------

@Dao
interface ConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ConfigEntity>)

    @Query("SELECT * FROM config WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): ConfigEntity?

    @Query("SELECT * FROM config")
    suspend fun getAll(): List<ConfigEntity>

    @Query("UPDATE config SET value_json = :valueJson, version = :version, updated_at = :updatedAt WHERE `key` = :key")
    suspend fun update(key: String, valueJson: String, version: Int, updatedAt: Long)

    @Query("DELETE FROM config WHERE updated_at < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM config")
    suspend fun count(): Int
}

// ---------------------------------------------------------------------------
// CommandDao
// ---------------------------------------------------------------------------

@Dao
interface CommandDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CommandEntity)

    @Query("SELECT * FROM commands WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CommandEntity?

    /** Commands awaiting processing by the agent. */
    @Query("SELECT * FROM commands WHERE status = 'pending' ORDER BY received_at ASC")
    suspend fun getPending(): List<CommandEntity>

    @Query("SELECT * FROM commands WHERE status = 'running'")
    suspend fun getRunning(): List<CommandEntity>

    @Query(
        """
        UPDATE commands
        SET status = :status, acked_at = :ackedAt, result_json = :resultJson
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        ackedAt: Long?,
        resultJson: String?
    )

    @Query("DELETE FROM commands WHERE received_at < :beforeTimestamp AND status IN ('acked','failed')")
    suspend fun deleteCompletedOlderThan(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM commands WHERE status = 'pending'")
    suspend fun countPending(): Int
}
