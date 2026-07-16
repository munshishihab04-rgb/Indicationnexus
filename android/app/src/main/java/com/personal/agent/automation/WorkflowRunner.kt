package com.personal.agent.automation

import android.util.Log
import com.personal.agent.core.db.AgentDatabase
import com.personal.agent.core.db.AutomationRunEntity
import kotlinx.coroutines.delay
import java.util.UUID

private const val TAG = "WorkflowRunner"

/**
 * Executes a [Workflow] step-by-step with full lifecycle tracking.
 *
 * Each run is persisted in the Room `automation_runs` table so it survives
 * restarts and can be inspected from the dashboard.
 *
 * Execution model:
 * 1. Create [AutomationRunEntity] with status=running.
 * 2. Evaluate each [WorkflowStep] in order via [ActionDispatcher].
 * 3. On step failure: apply [StepFailPolicy] (abort / continue / retry).
 * 4. Update run entity with status=succeeded/failed and result summary.
 */
class WorkflowRunner(
    private val db: AgentDatabase,
    private val dispatcher: ActionDispatcher
) {

    /**
     * Runs [workflow] and returns the final [RunResult].
     * Never throws — all errors are captured in the result.
     */
    suspend fun run(
        workflow: Workflow,
        triggerId: String? = null,
        initialVars: Map<String, Any> = emptyMap()
    ): RunResult {
        val runId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val ctx = RunContext(
            runId      = runId,
            workflowId = workflow.id,
            triggerId  = triggerId,
            vars       = initialVars.toMutableMap()
        )

        // Persist run entity
        db.automationRunDao().upsert(
            AutomationRunEntity(
                id           = runId,
                workflowId   = workflow.id,
                triggerId    = triggerId,
                currentStep  = null,
                status       = "running",
                attempts     = 1,
                startedAt    = now,
                finishedAt   = null,
                lastError    = null,
                resultSummary = null
            )
        )

        Log.i(TAG, "Run $runId started for workflow '${workflow.name}' (${workflow.steps.size} steps)")

        var overallSuccess = true
        var lastError: String? = null

        for (step in workflow.steps) {
            ctx.currentStep = step.id
            updateStep(runId, workflow.id, step.id, "running")

            val stepStart = System.currentTimeMillis()
            var stepOutcome = dispatcher.dispatch(step.action, ctx)

            // Retry logic
            if (!stepOutcome.success && step.onFail == StepFailPolicy.RETRY && step.retryCount > 0) {
                for (attempt in 1..step.retryCount) {
                    Log.d(TAG, "Step ${step.id} retry $attempt/${step.retryCount}")
                    delay(step.retryDelayMs)
                    stepOutcome = dispatcher.dispatch(step.action, ctx)
                    if (stepOutcome.success) break
                }
            }

            val duration = System.currentTimeMillis() - stepStart
            ctx.stepLog.add(
                StepLog(step.id, stepOutcome.success, stepOutcome.message, duration)
            )

            Log.d(TAG, "Step ${step.id} (${step.action.type}): " +
                "${if (stepOutcome.success) "OK" else "FAIL"} in ${duration}ms — ${stepOutcome.message}")

            if (!stepOutcome.success) {
                when (step.onFail) {
                    StepFailPolicy.ABORT -> {
                        overallSuccess = false
                        lastError = "${step.id}: ${stepOutcome.errorCode} — ${stepOutcome.message}"
                        Log.w(TAG, "Workflow aborted at step ${step.id}")
                        break
                    }
                    StepFailPolicy.CONTINUE -> {
                        Log.w(TAG, "Step ${step.id} failed, continuing")
                        // partial failure — don't abort
                    }
                    StepFailPolicy.RETRY -> {
                        // exhausted retries
                        overallSuccess = false
                        lastError = "${step.id}: retries exhausted — ${stepOutcome.message}"
                        break
                    }
                }
            }
        }

        val finishedAt = System.currentTimeMillis()
        val status = if (overallSuccess) "succeeded" else "failed"
        val summary = buildSummary(ctx, overallSuccess)

        // Final update
        db.automationRunDao().updateStatus(
            id           = runId,
            status       = status,
            currentStep  = null,
            attempts     = 1,
            finishedAt   = finishedAt,
            lastError    = lastError,
            resultSummary = summary
        )

        Log.i(TAG, "Run $runId $status in ${finishedAt - now}ms — ${ctx.stepLog.size} steps")

        return RunResult(
            runId    = runId,
            success  = overallSuccess,
            stepLogs = ctx.stepLog.toList(),
            vars     = ctx.vars.toMap(),
            durationMs = finishedAt - now,
            lastError = lastError
        )
    }

    private suspend fun updateStep(runId: String, workflowId: String, stepId: String, status: String) {
        db.automationRunDao().updateStatus(
            id           = runId,
            status       = "running",
            currentStep  = stepId,
            attempts     = 1,
            finishedAt   = null,
            lastError    = null,
            resultSummary = null
        )
    }

    private fun buildSummary(ctx: RunContext, success: Boolean): String {
        val total   = ctx.stepLog.size
        val passed  = ctx.stepLog.count { it.success }
        val failed  = total - passed
        return """{"total":$total,"passed":$passed,"failed":$failed,"success":$success}"""
    }
}

data class RunResult(
    val runId:     String,
    val success:   Boolean,
    val stepLogs:  List<StepLog>,
    val vars:      Map<String, Any>,
    val durationMs: Long,
    val lastError: String? = null
)
