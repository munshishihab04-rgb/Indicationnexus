package com.personal.agent.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────────────────────────
// Standard envelope
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "data") val data: T? = null,
    @Json(name = "meta") val meta: ResponseMeta? = null
)

@JsonClass(generateAdapter = true)
data class ResponseMeta(
    @Json(name = "requestId") val requestId: String,
    @Json(name = "serverTime") val serverTime: Long
)

@JsonClass(generateAdapter = true)
data class ApiError(
    @Json(name = "ok") val ok: Boolean = false,
    @Json(name = "error") val error: ErrorDetail,
    @Json(name = "meta") val meta: ResponseMeta? = null
)

@JsonClass(generateAdapter = true)
data class ErrorDetail(
    @Json(name = "code") val code: String,
    @Json(name = "message") val message: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Device — registration
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "setupToken") val setupToken: String? = null,
    @Json(name = "device") val device: DevicePayload
)

@JsonClass(generateAdapter = true)
data class DevicePayload(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "installId") val installId: String,
    @Json(name = "appVersion") val appVersion: String,
    @Json(name = "appVersionCode") val appVersionCode: Int,
    @Json(name = "manufacturer") val manufacturer: String,
    @Json(name = "model") val model: String,
    @Json(name = "androidVersion") val androidVersion: String,
    @Json(name = "sdk") val sdk: Int,
    @Json(name = "capabilities") val capabilities: List<String>,
    @Json(name = "createdAt") val createdAt: Long
)

@JsonClass(generateAdapter = true)
data class RegisterResponse(
    @Json(name = "apiKey") val apiKey: String,
    @Json(name = "deviceId") val deviceId: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Heartbeat
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "timestamp") val timestamp: Long,
    @Json(name = "battery") val battery: BatteryInfo,
    @Json(name = "network") val network: NetworkInfo,
    @Json(name = "storage") val storage: StorageInfo,
    @Json(name = "memory") val memory: MemoryInfo,
    @Json(name = "modules") val modules: List<ModuleStatusInfo>,
    @Json(name = "queueDepth") val queueDepth: Int,
    @Json(name = "configVersion") val configVersion: Int
)

@JsonClass(generateAdapter = true)
data class BatteryInfo(
    @Json(name = "percent") val percent: Int,
    @Json(name = "charging") val charging: Boolean,
    @Json(name = "temperatureC") val temperatureC: Float,
    @Json(name = "powerSaveMode") val powerSaveMode: Boolean
)

@JsonClass(generateAdapter = true)
data class NetworkInfo(
    @Json(name = "transport") val transport: String,
    @Json(name = "metered") val metered: Boolean,
    @Json(name = "connected") val connected: Boolean
)

@JsonClass(generateAdapter = true)
data class StorageInfo(
    @Json(name = "totalBytes") val totalBytes: Long,
    @Json(name = "freeBytes") val freeBytes: Long
)

@JsonClass(generateAdapter = true)
data class MemoryInfo(
    @Json(name = "totalBytes") val totalBytes: Long,
    @Json(name = "availableBytes") val availableBytes: Long,
    @Json(name = "lowMemory") val lowMemory: Boolean
)

@JsonClass(generateAdapter = true)
data class ModuleStatusInfo(
    @Json(name = "id") val id: String,
    @Json(name = "status") val status: String,
    @Json(name = "queueDepth") val queueDepth: Int
)

// ─────────────────────────────────────────────────────────────────────────────
// Config
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class ConfigResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "version") val version: Int,
    @Json(name = "config") val config: RemoteConfigPayload? = null
)

@JsonClass(generateAdapter = true)
data class RemoteConfigPayload(
    @Json(name = "version") val version: Int,
    @Json(name = "modules") val modules: Map<String, ModuleConfigPayload>,
    @Json(name = "intervals") val intervals: IntervalsPayload,
    @Json(name = "limits") val limits: LimitsPayload,
    @Json(name = "privacy") val privacy: PrivacyPayload,
    @Json(name = "apps") val apps: AppsPayload
)

@JsonClass(generateAdapter = true)
data class ModuleConfigPayload(
    @Json(name = "enabled") val enabled: Boolean
)

@JsonClass(generateAdapter = true)
data class IntervalsPayload(
    @Json(name = "heartbeatSeconds") val heartbeatSeconds: Int,
    @Json(name = "configSyncMinutes") val configSyncMinutes: Int,
    @Json(name = "logUploadMinutes") val logUploadMinutes: Int
)

@JsonClass(generateAdapter = true)
data class LimitsPayload(
    @Json(name = "actionsPerMinute") val actionsPerMinute: Int,
    @Json(name = "maxWorkflowSteps") val maxWorkflowSteps: Int,
    @Json(name = "maxQueueSize") val maxQueueSize: Int
)

@JsonClass(generateAdapter = true)
data class PrivacyPayload(
    @Json(name = "storeScreenshots") val storeScreenshots: Boolean,
    @Json(name = "uploadScreenshots") val uploadScreenshots: Boolean,
    @Json(name = "redactPatterns") val redactPatterns: List<String>
)

@JsonClass(generateAdapter = true)
data class AppsPayload(
    @Json(name = "allowlist") val allowlist: List<String>,
    @Json(name = "denylist") val denylist: List<String>
)

// ─────────────────────────────────────────────────────────────────────────────
// Commands
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class CommandsResponse(
    @Json(name = "commands") val commands: List<CommandPayload>
)

@JsonClass(generateAdapter = true)
data class CommandPayload(
    @Json(name = "id") val id: String,
    @Json(name = "type") val type: String,
    @Json(name = "payload") val payload: Map<String, Any>,
    @Json(name = "createdAt") val createdAt: Long
)

@JsonClass(generateAdapter = true)
data class AckRequest(
    @Json(name = "commandId") val commandId: String,
    @Json(name = "status") val status: String,
    @Json(name = "result") val result: Map<String, Any>? = null,
    @Json(name = "timestamp") val timestamp: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// Logs
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class LogUploadRequest(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "logs") val logs: List<LogPayload>
)

@JsonClass(generateAdapter = true)
data class LogPayload(
    @Json(name = "id") val id: String,
    @Json(name = "level") val level: String,
    @Json(name = "module") val module: String,
    @Json(name = "event") val event: String,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: Map<String, Any>? = null,
    @Json(name = "createdAt") val createdAt: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// Notification
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class NotificationRequest(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "title") val title: String,
    @Json(name = "body") val body: String,
    @Json(name = "data") val data: Map<String, Any>? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Vision / OCR
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class VisionAnalyzeRequest(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "prompt") val prompt: String,
    @Json(name = "imageBase64") val imageBase64: String? = null,
    @Json(name = "context") val context: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class VisionAnalyzeResponse(
    @Json(name = "result") val result: String,
    @Json(name = "confidence") val confidence: Float? = null,
    @Json(name = "elements") val elements: List<Map<String, Any>>? = null
)

@JsonClass(generateAdapter = true)
data class OcrResponse(
    @Json(name = "text") val text: String,
    @Json(name = "blocks") val blocks: List<Map<String, Any>>? = null,
    @Json(name = "confidence") val confidence: Float? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Automation
// ─────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AutomationRunRequest(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "workflowId") val workflowId: String,
    @Json(name = "trigger") val trigger: String,
    @Json(name = "params") val params: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class AutomationRunResponse(
    @Json(name = "runId") val runId: String,
    @Json(name = "status") val status: String,
    @Json(name = "startedAt") val startedAt: Long
)
