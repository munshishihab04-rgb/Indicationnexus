package com.personal.agent.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// DeviceEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "device")
data class DeviceEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "install_id")
    val installId: String,

    @ColumnInfo(name = "enrolled")
    val enrolled: Boolean,

    @ColumnInfo(name = "api_key_alias")
    val apiKeyAlias: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

// ---------------------------------------------------------------------------
// JobEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    /** queued | running | succeeded | failed | cancelled */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "priority")
    val priority: Int,

    @ColumnInfo(name = "attempt")
    val attempt: Int,

    @ColumnInfo(name = "max_attempts")
    val maxAttempts: Int,

    @ColumnInfo(name = "not_before")
    val notBefore: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "last_error")
    val lastError: String?
)

// ---------------------------------------------------------------------------
// AutomationEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "automation")
data class AutomationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "workflow_json")
    val workflowJson: String,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,

    @ColumnInfo(name = "version")
    val version: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

// ---------------------------------------------------------------------------
// AutomationRunEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "automation_runs")
data class AutomationRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "workflow_id")
    val workflowId: String,

    @ColumnInfo(name = "trigger_id")
    val triggerId: String?,

    @ColumnInfo(name = "current_step")
    val currentStep: String?,

    /** queued | running | succeeded | failed | cancelled */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "attempts")
    val attempts: Int,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?,

    @ColumnInfo(name = "last_error")
    val lastError: String?,

    @ColumnInfo(name = "result_summary")
    val resultSummary: String?
)

// ---------------------------------------------------------------------------
// NotificationEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String?,

    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "body")
    val body: String?,

    @ColumnInfo(name = "sender")
    val sender: String?,

    @ColumnInfo(name = "conversation")
    val conversation: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long?
)

// ---------------------------------------------------------------------------
// ScreenEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "screens")
data class ScreenEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "package_name")
    val packageName: String?,

    @ColumnInfo(name = "class_name")
    val className: String?,

    @ColumnInfo(name = "screen_type")
    val screenType: String?,

    @ColumnInfo(name = "accessibility_json")
    val accessibilityJson: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

// ---------------------------------------------------------------------------
// OcrEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "ocr")
data class OcrEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "screen_id")
    val screenId: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "bounds_json")
    val boundsJson: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

// ---------------------------------------------------------------------------
// VisionEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "vision")
data class VisionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "screen_id")
    val screenId: String,

    @ColumnInfo(name = "result_json")
    val resultJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

// ---------------------------------------------------------------------------
// LogEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "level")
    val level: String,

    @ColumnInfo(name = "module")
    val module: String,

    @ColumnInfo(name = "event")
    val event: String,

    @ColumnInfo(name = "message")
    val message: String?,

    @ColumnInfo(name = "data_json")
    val dataJson: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long?
)

// ---------------------------------------------------------------------------
// PermissionEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "permissions")
data class PermissionEntity(
    /** e.g. "android.permission.CAMERA" or a custom module key */
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "granted")
    val granted: Boolean,

    /** JSON array of module IDs that require this permission */
    @ColumnInfo(name = "required_by")
    val requiredBy: String,

    @ColumnInfo(name = "last_checked_at")
    val lastCheckedAt: Long,

    @ColumnInfo(name = "last_prompted_at")
    val lastPromptedAt: Long?,

    @ColumnInfo(name = "settings_action")
    val settingsAction: String?
)

// ---------------------------------------------------------------------------
// ConfigEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value_json")
    val valueJson: String,

    @ColumnInfo(name = "version")
    val version: Int,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

// ---------------------------------------------------------------------------
// CommandEntity
// ---------------------------------------------------------------------------

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    /** pending | running | acked | failed */
    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    @ColumnInfo(name = "acked_at")
    val ackedAt: Long?,

    @ColumnInfo(name = "result_json")
    val resultJson: String?
)
