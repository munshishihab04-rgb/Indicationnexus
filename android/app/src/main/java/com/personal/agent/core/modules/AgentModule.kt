package com.personal.agent.core.modules

enum class HealthStatus { HEALTHY, DEGRADED, ERROR, STOPPED }

data class ModuleHealth(
    val id: String,
    val status: HealthStatus,
    val lastSuccessAt: Long? = null,
    val lastErrorAt: Long? = null,
    val lastErrorCode: String? = null,
    val queueDepth: Int = 0
)

data class ModuleConfig(
    val enabled: Boolean,
    val minBatteryPercent: Int = 20,
    val wifiOnly: Boolean = false,
    val extras: Map<String, Any> = emptyMap()
)

data class CommandResult(
    val commandId: String,
    val success: Boolean,
    val data: Map<String, Any> = emptyMap(),
    val errorCode: String? = null
)

data class AgentCommand(
    val id: String,
    val type: String,
    val payload: Map<String, Any> = emptyMap()
)

interface AgentModule {
    val id: String
    val version: Int
    suspend fun initialize(context: ModuleContext)
    suspend fun start(config: ModuleConfig)
    suspend fun stop(reason: String)
    suspend fun applyConfig(config: ModuleConfig)
    suspend fun health(): ModuleHealth
    suspend fun handleCommand(command: AgentCommand): CommandResult
}
