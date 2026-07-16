package com.personal.agent.core.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface AgentApi {

    @POST("v1/device/register")
    suspend fun registerDevice(
        @Body request: RegisterRequest
    ): ApiResponse<RegisterResponse>

    @POST("v1/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: HeartbeatRequest
    ): ApiResponse<Map<String, Any>>

    @GET("v1/config")
    suspend fun getConfig(
        @Query("deviceId") deviceId: String,
        @Query("currentVersion") currentVersion: Int
    ): ApiResponse<ConfigData>

    @GET("v1/commands")
    suspend fun getCommands(
        @Query("limit") limit: Int = 20
    ): ApiResponse<CommandsData>

    @POST("v1/ack")
    suspend fun ackCommand(
        @Body request: AckRequest
    ): ApiResponse<Map<String, Any>>

    @POST("v1/logs")
    suspend fun uploadLogs(
        @Body request: LogUploadRequest
    ): ApiResponse<Map<String, Any>>

    @POST("v1/notification")
    suspend fun uploadNotification(
        @Body payload: NotificationPayload
    ): ApiResponse<Map<String, Any>>

    @Multipart
    @POST("v1/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("deviceId") deviceId: RequestBody,
        @Part("fileType") fileType: RequestBody
    ): ApiResponse<Map<String, Any>>

    @POST("v1/vision/analyze")
    suspend fun analyzeVision(
        @Body request: VisionRequest
    ): ApiResponse<Map<String, Any>>

    @POST("v1/ocr")
    suspend fun analyzeOcr(
        @Body request: OcrRequest
    ): ApiResponse<Map<String, Any>>

    @POST("v1/automation/run")
    suspend fun submitAutomationRun(
        @Body request: AutomationRunRequest
    ): ApiResponse<Map<String, Any>>
}
