package com.personal.agent.automation

import com.personal.agent.core.db.AgentDatabase
import com.personal.agent.core.db.AutomationEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

/**
 * CRUD operations on [AutomationEntity] backed by Room.
 * Serializes/deserializes [Workflow] to/from JSON using Moshi.
 */
class WorkflowRepository(private val db: AgentDatabase) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val workflowAdapter = moshi.adapter(Workflow::class.java)

    /** Returns all enabled workflows. */
    suspend fun getEnabled(): List<Workflow> =
        db.automationDao().getEnabled().mapNotNull { deserialize(it) }

    /** Returns all workflows (enabled + disabled). */
    suspend fun getAll(): List<Workflow> =
        db.automationDao().getAll().mapNotNull { deserialize(it) }

    /** Returns a single workflow by ID. */
    suspend fun findById(id: String): Workflow? =
        db.automationDao().findById(id)?.let { deserialize(it) }

    /** Saves (create or update) a workflow. */
    suspend fun save(workflow: Workflow) {
        val json = workflowAdapter.toJson(workflow)
        db.automationDao().upsert(
            AutomationEntity(
                id          = workflow.id,
                name        = workflow.name,
                workflowJson = json,
                enabled     = workflow.enabled,
                version     = workflow.version,
                createdAt   = workflow.createdAt,
                updatedAt   = System.currentTimeMillis()
            )
        )
    }

    /** Enables or disables a workflow. */
    suspend fun setEnabled(id: String, enabled: Boolean) {
        db.automationDao().updateEnabled(id, enabled, System.currentTimeMillis())
    }

    /** Deletes old disabled workflows. */
    suspend fun pruneOld(olderThanMs: Long) {
        db.automationDao().deleteDisabledOlderThan(olderThanMs)
    }

    private fun deserialize(entity: AutomationEntity): Workflow? = try {
        workflowAdapter.fromJson(entity.workflowJson)
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Builds a simple Workflow from a minimal JSON payload (for remote creation). */
        fun fromJson(json: String, moshi: Moshi): Workflow? = try {
            moshi.adapter(Workflow::class.java).fromJson(json)
        } catch (_: Exception) { null }
    }
}
