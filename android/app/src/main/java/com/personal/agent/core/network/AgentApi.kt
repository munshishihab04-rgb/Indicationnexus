package com.personal.agent.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Retrofit interface defining every endpoint exposed by the personal-agent backend.
 *
 * All suspend functions return [Response] so callers can inspect HTTP status codes
 * alongside the deserialized body without throwing on 4xx/5xx.
 *
 * Base URL is injected by [NetworkClient]; every path here is relative.
 */
interface AgentApi {

    // ─────────────────────────────────────────────────────────────────────────
    // Device
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Register this device with the backend and obtain an API key.
     * No Authorization header is required for this call; [NetworkClient]
     * skips the Bearer header when [NetworkClient.apiKey] is null.
     */
    @POST("v1/device/register")
    suspend fun registerDevice(
        @Body request: RegisterRequest
    ): Response<ApiResponse<RegisterResponse>>

    // ─────────────────────────────────────────────────────────────────────────
    // Heartbeat
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Send a periodic heartbeat containing device telemetry and module status.
     */
    @POST("v1/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: HeartbeatRequest
    ): Response<ApiResponse<Unit>>

    // ─────────────────────────────────────────────────────────────────────────
    // Config
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetch the latest remote configuration.
     * The server returns 304-equivalent (ok=true, config=null) when
     * [currentVersion] matches the server-side version.
     */
    @GET("v1/config")
    suspend fun getConfig(
        @Query("deviceId") deviceId: String,
        @Query("currentVersion") currentVersion: Int
    ): Response<ConfigResponse>

    // ─────────────────────────────────────────────────────────────────────────
    // Commands
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Poll for pending commands destined for this device.
     */
    @GET("v1/commands")
    suspend fun getCommands(
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<CommandsResponse>>

    /**
     * Acknowledge a command after execution, reporting success or failure.
     */
    @POST("v1/ack")
    suspend fun ackCommand(
        @Body request: AckRequest
    ): Response<ApiResponse<Unit>>

    // ─────────────────────────────────────────────────────────────────────────
    // Logs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Upload a batch of structured log entries to the backend.
     */
    @POST("v1/logs")
    suspend fun uploadLogs(
        @Body request: LogUploadRequest
    ): Response<ApiResponse<Unit>>

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Send a push-notification request to be delivered to the device (or others).
     */
    @POST("v1/notification")
    suspend fun sendNotification(
        @Body request: NotificationRequest
    ): Response<ApiResponse<Unit>>

    // ─────────────────────────────────────────────────────────────────────────
    // File upload
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Upload a binary file (screenshot, recording, etc.) as multipart form data.
     *
     * @param file    The file part; name it "file" server-side.
     * @param meta    Optional JSON metadata part; name it "meta" server-side.
     * @param deviceId Device identifier part.
     */
    @Multipart
    @POST("v1/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("deviceId") deviceId: RequestBody,
        @Part("meta") meta: RequestBody? = null
    ): Response<ApiResponse<Map<String, Any>>>

    // ─────────────────────────────────────────────────────────────────────────
    // Vision
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Run vision analysis (LLM-backed) on an image already uploaded or
     * base64-encoded inline in the request body.
     */
    @POST("v1/vision/analyze")
    suspend fun analyzeVision(
        @Body request: VisionAnalyzeRequest
    ): Response<ApiResponse<VisionAnalyzeResponse>>

    // ─────────────────────────────────────────────────────────────────────────
    // OCR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extract text from an image via multipart upload.
     *
     * @param image    The image file part.
     * @param deviceId Device identifier part.
     * @param language Optional BCP-47 language hint (e.g. "en").
     */
    @Multipart
    @POST("v1/ocr")
    suspend fun runOcr(
        @Part image: MultipartBody.Part,
        @Part("deviceId") deviceId: RequestBody,
        @Part("language") language: RequestBody? = null
    ): Response<ApiResponse<OcrResponse>>

    // ─────────────────────────────────────────────────────────────────────────
    // Automation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Trigger a named automation workflow on the backend.
     */
    @POST("v1/automation/run")
    suspend fun runAutomation(
        @Body request: AutomationRunRequest
    ): Response<ApiResponse<AutomationRunResponse>>
}
