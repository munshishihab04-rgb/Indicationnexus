package com.personal.agent.automation

import android.util.Log
import com.personal.agent.core.modules.AgentCommand
import com.personal.agent.core.modules.AgentModule
import com.personal.agent.core.modules.CommandResult
import com.personal.agent.core.modules.HealthStatus
import com.personal.agent.core.modules.ModuleConfig
import com.personal.agent.core.modules.ModuleContext
import com.personal.agent.core.modules.ModuleHealth
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "AutomationEngine"

/**
 * Phase 7 — Automation Engine [AgentModule].
 *
 * Responsibilities:
 * - Receive [AgentEvent] from other modules and evaluate triggers.
 * - Load enabled [Workflow]s from [WorkflowRepository].
 * - Run matching workflows via [WorkflowRunner].
 * - Handle remote commands: install_workflow, run_workflow, list_workflows, enable/disable.
 *
 * Event flow:
 *   Other module emits event → AutomationEngine.onEvent(event)
 *   → match trigger on enabled workflows → WorkflowRunner.run()
 */
class AutomationEngine : AgentModule {

    override val id: String = MODULE_ID
    override val version: Int = 1

    private lateinit var ctx: ModuleContext
    private lateinit var repo: WorkflowRepository
    private lateinit var runner: WorkflowRunner
    private lateinit var dispatcher: ActionDispatcher

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private var runsTotal:     Long = 0L
    private var runsSucceeded: Long = 0L
    private var runsFailed:    Long = 0L
    private var lastErrorCode: String? = null
    private var startedAt:     Long? = null

    override suspend fun initialize(context: ModuleContext) {
        ctx        = context
        repo       = WorkflowRepository(ctx.db)
        dispatcher = ActionDispatcher(ctx.appContext)
        runner     = WorkflowRunner(ctx.db, dispatcher)
        AutomationEngineState.engine = this
        Log.i(TAG, "AutomationEngine initialized")
    }

    override suspend fun start(config: ModuleConfig) {
        AutomationEngineState.enabled = true
        startedAt = System.currentTimeMillis()
        Log.i(TAG, "AutomationEngine started")
        ctx.logger.info(id, "engine_started", "Automation engine enabled")
    }

    override suspend fun stop(reason: String) {
        AutomationEngineState.enabled = false
        Log.i(TAG, "AutomationEngine stopped: $reason")
    }

    override suspend fun applyConfig(config: ModuleConfig) {
        if (!config.enabled && AutomationEngineState.enabled) stop("disabled_by_config")
        else if (config.enabled && !AutomationEngineState.enabled) start(config)
    }

    override suspend fun health(): ModuleHealth {
        val active = try { ctx.db.automationRunDao().countPending() } catch (_: Exception) { 0 }
        return ModuleHealth(
            id            = id,
            status        = if (AutomationEngineState.enabled) HealthStatus.HEALTHY else HealthStatus.STOPPED,
            lastSuccessAt = startedAt,
            lastErrorCode = lastErrorCode,
            queueDepth    = active
        )
    }

    override suspend fun handleCommand(command: AgentCommand): CommandResult {
        return when (command.type) {

            "install_workflow" -> {
                val json = command.payload["workflowJson"] as? String
                    ?: return CommandResult(command.id, false, errorCode = "no_workflow_json")
                val workflow = WorkflowRepository.fromJson(json, moshi)
                    ?: return CommandResult(command.id, false, errorCode = "invalid_workflow_json")
                repo.save(workflow)
                ctx.logger.info(id, "workflow_installed", "'${workflow.name}' [${workflow.id}]")
                CommandResult(command.id, true,
                    data = mapOf("workflowId" to workflow.id, "name" to workflow.name))
            }

            "run_workflow" -> {
                val workflowId = command.payload["workflowId"] as? String
                    ?: return CommandResult(command.id, false, errorCode = "no_workflow_id")
                val workflow = repo.findById(workflowId)
                    ?: return CommandResult(command.id, false, errorCode = "workflow_not_found")

                @Suppress("UNCHECKED_CAST")
                val vars = command.payload["vars"] as? Map<String, Any> ?: emptyMap()
                val result = runWorkflow(workflow, null, vars)
                CommandResult(
                    commandId = command.id,
                    success   = result.success,
                    data      = mapOf(
                        "runId"      to result.runId,
                        "steps"      to result.stepLogs.size,
                        "durationMs" to result.durationMs
                    ),
                    errorCode = result.lastError
                )
            }

            "list_workflows" -> {
                val all = repo.getAll()
                CommandResult(
                    commandId = command.id,
                    success   = true,
                    data      = mapOf(
                        "total"   to all.size,
                        "enabled" to all.count { it.enabled },
                        "workflows" to all.map { mapOf("id" to it.id, "name" to it.name, "enabled" to it.enabled) }
                    )
                )
            }

            "enable_workflow" -> {
                val wid = command.payload["workflowId"] as? String
                    ?: return CommandResult(command.id, false, errorCode = "no_workflow_id")
                repo.setEnabled(wid, true)
                CommandResult(command.id, true, data = mapOf("workflowId" to wid, "enabled" to true))
            }

            "disable_workflow" -> {
                val wid = command.payload["workflowId"] as? String
                    ?: return CommandResult(command.id, false, errorCode = "no_workflow_id")
                repo.setEnabled(wid, false)
                CommandResult(command.id, true, data = mapOf("workflowId" to wid, "enabled" to false))
            }

            "get_stats" -> CommandResult(
                commandId = command.id,
                success   = true,
                data      = mapOf(
                    "enabled"       to AutomationEngineState.enabled,
                    "runsTotal"     to runsTotal,
                    "runsSucceeded" to runsSucceeded,
                    "runsFailed"    to runsFailed,
                    "lastErrorCode" to (lastErrorCode ?: "none")
                )
            )

            else -> CommandResult(command.id, false, errorCode = "unknown_command")
        }
    }

    // ─── Event bus ────────────────────────────────────────────────────────────

    /**
     * Called by other modules when an event occurs.
     * Evaluates all enabled workflow triggers and fires matching ones asynchronously.
     */
    fun onEvent(event: AgentEvent) {
        if (!AutomationEngineState.enabled) return
        engineScope.launch {
            try {
                val workflows = repo.getEnabled()
                for (workflow in workflows) {
                    if (TriggerEvaluator.matches(workflow.trigger, event) &&
                        ConditionChecker.allPass(workflow.conditions, ctx.appContext)) {
                        Log.i(TAG, "Workflow '${workflow.name}' triggered by ${event::class.simpleName}")
                        runWorkflow(workflow, triggerId = null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onEvent error: ${e.message}")
            }
        }
    }

    private suspend fun runWorkflow(
        workflow: Workflow,
        triggerId: String?,
        vars: Map<String, Any> = emptyMap()
    ): RunResult {
        runsTotal++
        return try {
            val result = runner.run(workflow, triggerId, vars)
            if (result.success) runsSucceeded++ else runsFailed++
            lastErrorCode = result.lastError?.take(100)
            result
        } catch (e: Exception) {
            runsFailed++
            lastErrorCode = e.javaClass.simpleName
            Log.e(TAG, "Workflow run exception: ${e.message}")
            RunResult(
                runId     = UUID.randomUUID().toString(),
                success   = false,
                stepLogs  = emptyList(),
                vars      = emptyMap(),
                durationMs = 0,
                lastError = e.message
            )
        }
    }

    companion object {
        const val MODULE_ID = "automation_engine"
    }
}

object AutomationEngineState {
    @Volatile var enabled: Boolean = false
    @Volatile var engine: AutomationEngine? = null
}
