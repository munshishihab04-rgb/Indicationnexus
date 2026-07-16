package com.personal.agent.core.modules

import com.personal.agent.core.config.RemoteConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Central registry responsible for lifecycle management of all [AgentModule] instances.
 *
 * Responsibilities:
 *  - Parse module enable/disable state from [RemoteConfig.modulesJson].
 *  - Start modules that are enabled (and not yet running).
 *  - Stop modules that are disabled (or no longer present in the config).
 *  - Dispatch [AgentCommand] instances to the appropriate module by id.
 *  - Collect [ModuleHealth] snapshots from every registered module.
 *
 * Exception isolation: every module operation is wrapped in its own try/catch so that
 * a failure in one module never prevents the others from being started, stopped, or queried.
 *
 * @param modules    Full set of [AgentModule] implementations known to the application.
 * @param context    Shared [ModuleContext] injected into each module during initialization.
 */
class ModuleRegistry(
    private val modules: Set<AgentModule>,
    private val context: ModuleContext
) {

    // Tracks which module ids have been successfully started.
    private val runningModuleIds: MutableSet<String> = mutableSetOf()

    // Moshi instance for parsing modulesJson.
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Type adapter for Map<String, ModuleConfig>.
    private val moduleConfigMapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        ModuleConfig::class.java
    )
    private val moduleConfigMapAdapter = moshi.adapter<Map<String, ModuleConfig>>(moduleConfigMapType)

    /**
     * Parses the current remote config and starts every module whose id appears in the
     * config with [ModuleConfig.enabled] == true and is not already running.
     *
     * Each module is initialized (if it hasn't been yet) and then started. Failures in
     * individual modules are caught and logged without affecting other modules.
     */
    suspend fun startEnabledModules() {
        val config = context.configStore?.getConfig() ?: run {
            context.logger.warn(
                module = "ModuleRegistry",
                event = "start_enabled_skipped",
                message = "No remote config available; skipping startEnabledModules()",
                data = null
            )
            return
        }

        val moduleConfigs = parseModuleConfigs(config.modulesJson)

        supervisorScope {
            modules.map { module ->
                async {
                    val moduleConfig = moduleConfigs[module.id]
                    if (moduleConfig == null || !moduleConfig.enabled) return@async

                    try {
                        if (!runningModuleIds.contains(module.id)) {
                            context.logger.info(
                                module = "ModuleRegistry",
                                event = "module_initializing",
                                message = "Initializing module: ${module.id} v${module.version}",
                                data = null
                            )
                            module.initialize(context)
                        }

                        context.logger.info(
                            module = "ModuleRegistry",
                            event = "module_starting",
                            message = "Starting module: ${module.id} v${module.version}",
                            data = mapOf("enabled" to true, "wifiOnly" to moduleConfig.wifiOnly)
                        )
                        module.start(moduleConfig)
                        runningModuleIds.add(module.id)

                        context.logger.info(
                            module = "ModuleRegistry",
                            event = "module_started",
                            message = "Module started successfully: ${module.id}",
                            data = null
                        )
                    } catch (e: Exception) {
                        context.logger.error(
                            module = "ModuleRegistry",
                            event = "module_start_failed",
                            message = "Failed to start module ${module.id}: ${e.message}",
                            data = mapOf("error" to (e.message ?: "unknown"))
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Parses the current remote config and stops every running module whose id either
     * does not appear in the config or has [ModuleConfig.enabled] == false.
     *
     * Each module is stopped with the reason "disabled_by_config". Failures are caught
     * and logged per module.
     */
    suspend fun stopDisabledModules() {
        val config = context.configStore?.getConfig()
        val moduleConfigs: Map<String, ModuleConfig> = if (config != null) {
            parseModuleConfigs(config.modulesJson)
        } else {
            emptyMap()
        }

        supervisorScope {
            modules
                .filter { runningModuleIds.contains(it.id) }
                .filter { module ->
                    val mc = moduleConfigs[module.id]
                    mc == null || !mc.enabled
                }
                .map { module ->
                    async {
                        try {
                            context.logger.info(
                                module = "ModuleRegistry",
                                event = "module_stopping",
                                message = "Stopping module: ${module.id} — reason: disabled_by_config",
                                data = null
                            )
                            module.stop(reason = "disabled_by_config")
                            runningModuleIds.remove(module.id)

                            context.logger.info(
                                module = "ModuleRegistry",
                                event = "module_stopped",
                                message = "Module stopped: ${module.id}",
                                data = null
                            )
                        } catch (e: Exception) {
                            context.logger.error(
                                module = "ModuleRegistry",
                                event = "module_stop_failed",
                                message = "Failed to stop module ${module.id}: ${e.message}",
                                data = mapOf("error" to (e.message ?: "unknown"))
                            )
                        }
                    }
                }.awaitAll()
        }
    }

    /**
     * Routes [command] to the module whose [AgentModule.id] matches [AgentCommand.type].
     *
     * If no matching module is found, or the target module is not currently running,
     * a failure [CommandResult] is returned immediately.
     *
     * @return [CommandResult] produced by the module, or an error result if routing fails.
     */
    suspend fun dispatchCommand(command: AgentCommand): CommandResult {
        val targetModule = modules.find { it.id == command.type }

        if (targetModule == null) {
            context.logger.warn(
                module = "ModuleRegistry",
                event = "command_no_target",
                message = "No module found for command type '${command.type}' (commandId=${command.id})",
                data = mapOf("commandId" to command.id, "type" to command.type)
            )
            return CommandResult(
                commandId = command.id,
                success = false,
                errorCode = "MODULE_NOT_FOUND"
            )
        }

        if (!runningModuleIds.contains(targetModule.id)) {
            context.logger.warn(
                module = "ModuleRegistry",
                event = "command_module_not_running",
                message = "Module '${targetModule.id}' is not running; cannot handle command ${command.id}",
                data = mapOf("commandId" to command.id, "moduleId" to targetModule.id)
            )
            return CommandResult(
                commandId = command.id,
                success = false,
                errorCode = "MODULE_NOT_RUNNING"
            )
        }

        return try {
            context.logger.debug(
                module = "ModuleRegistry",
                event = "command_dispatching",
                message = "Dispatching command ${command.id} to module '${targetModule.id}'",
                data = mapOf("commandId" to command.id, "type" to command.type)
            )
            val result = targetModule.handleCommand(command)
            context.logger.debug(
                module = "ModuleRegistry",
                event = "command_dispatched",
                message = "Command ${command.id} handled by '${targetModule.id}': success=${result.success}",
                data = mapOf("commandId" to command.id, "success" to result.success)
            )
            result
        } catch (e: Exception) {
            context.logger.error(
                module = "ModuleRegistry",
                event = "command_dispatch_failed",
                message = "Exception handling command ${command.id} in module '${targetModule.id}': ${e.message}",
                data = mapOf("commandId" to command.id, "error" to (e.message ?: "unknown"))
            )
            CommandResult(
                commandId = command.id,
                success = false,
                errorCode = "DISPATCH_EXCEPTION"
            )
        }
    }

    /**
     * Collects a [ModuleHealth] snapshot from every registered module concurrently.
     *
     * Failures in individual health checks are caught; the affected module will appear
     * in the returned list with [HealthStatus.ERROR].
     *
     * @return List of [ModuleHealth] — one entry per registered module.
     */
    suspend fun collectHealth(): List<ModuleHealth> {
        return supervisorScope {
            modules.map { module ->
                async {
                    try {
                        module.health()
                    } catch (e: Exception) {
                        context.logger.error(
                            module = "ModuleRegistry",
                            event = "health_check_failed",
                            message = "Health check threw for module '${module.id}': ${e.message}",
                            data = mapOf("moduleId" to module.id, "error" to (e.message ?: "unknown"))
                        )
                        ModuleHealth(
                            id = module.id,
                            status = HealthStatus.ERROR,
                            lastErrorAt = System.currentTimeMillis(),
                            lastErrorCode = "HEALTH_CHECK_EXCEPTION"
                        )
                    }
                }
            }.awaitAll()
        }
    }

    // -----------------------------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Parses a JSON string of the form `{ "<moduleId>": { ModuleConfig fields }, ... }` into
     * a map from module id to [ModuleConfig]. Returns an empty map on any parse error.
     */
    private fun parseModuleConfigs(modulesJson: String): Map<String, ModuleConfig> {
        return try {
            moduleConfigMapAdapter.fromJson(modulesJson) ?: emptyMap()
        } catch (e: Exception) {
            context.logger.error(
                module = "ModuleRegistry",
                event = "config_parse_failed",
                message = "Failed to parse modulesJson: ${e.message}",
                data = mapOf("error" to (e.message ?: "unknown"))
            )
            emptyMap()
        }
    }
}
