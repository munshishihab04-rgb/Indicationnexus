# 02 — Module Architecture

## Module Interface

Every capability should implement a common contract.

```kotlin
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
```

## Module Registry

The registry owns lifecycle and configuration.

```kotlin
class ModuleRegistry(
    private val modules: Set<AgentModule>,
    private val configStore: ConfigStore,
    private val logger: AgentLogger
) {
    suspend fun startEnabledModules()
    suspend fun stopDisabledModules()
    suspend fun dispatchCommand(command: AgentCommand): CommandResult
    suspend fun collectHealth(): List<ModuleHealth>
}
```

## Module Context

`ModuleContext` should provide controlled access to shared services:

- application context;
- Room database;
- network client;
- logger;
- job queue;
- config store;
- permission manager;
- action executor;
- clock;
- coroutine scope.

## Module Configuration

```json
{
  "id": "accessibility_engine",
  "enabled": true,
  "minBatteryPercent": 20,
  "wifiOnly": false,
  "retry": {
    "maxAttempts": 5,
    "baseDelayMs": 1000,
    "maxDelayMs": 60000
  },
  "limits": {
    "eventsPerMinute": 60,
    "maxQueueSize": 10000
  }
}
```

## Lifecycle Rules

1. `initialize()` runs once after app startup.
2. `start()` runs only when remote config enables the module and required permissions are present.
3. `applyConfig()` updates intervals/limits without restarting where possible.
4. `stop()` must cancel jobs, listeners, and background work.
5. `health()` must never block and must not expose sensitive content.
6. `handleCommand()` must validate command scope and module state.

## Error Handling

Each module returns structured results:

```kotlin
data class ModuleHealth(
    val id: String,
    val status: HealthStatus,
    val lastSuccessAt: Long?,
    val lastErrorAt: Long?,
    val lastErrorCode: String?,
    val queueDepth: Int
)
```

## Adding a New Module

Checklist:

- [ ] Create module package.
- [ ] Implement `AgentModule`.
- [ ] Add Room entities if needed.
- [ ] Add config schema.
- [ ] Add command schema if needed.
- [ ] Add permission declarations if needed.
- [ ] Register module in dependency graph.
- [ ] Add tests.
- [ ] Add API documentation.
