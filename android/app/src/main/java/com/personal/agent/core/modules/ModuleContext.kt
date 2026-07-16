package com.personal.agent.core.modules

import android.content.Context
import com.personal.agent.core.config.ConfigStore
import com.personal.agent.core.db.AgentDatabase
import com.personal.agent.core.logging.AgentLogger
import kotlinx.coroutines.CoroutineScope

/**
 * Provides controlled access to shared application services for agent modules.
 *
 * Each [AgentModule] receives a [ModuleContext] during [AgentModule.initialize] and
 * uses it to interact with the database, logging, config, and the shared coroutine scope
 * instead of holding direct references to application-level singletons.
 *
 * @property appContext Application-level Android [Context]. Never pass an Activity context here.
 * @property db         Room database instance shared by all modules.
 * @property logger     Structured logger for writing and uploading log entries.
 * @property configStore Accessor for the cached [com.personal.agent.core.config.RemoteConfig].
 * @property scope      [CoroutineScope] tied to the agent service lifetime. All module
 *                      coroutines should be launched in this scope so they are cancelled
 *                      when the service shuts down.
 */
data class ModuleContext(
    val appContext: Context,
    val db: AgentDatabase,
    val logger: AgentLogger,
    val configStore: ConfigStore?,
    val scope: CoroutineScope
)
