package com.personal.agent.vision

import android.content.Context
import android.util.Log
import com.personal.agent.core.modules.AgentCommand
import com.personal.agent.core.modules.AgentModule
import com.personal.agent.core.modules.CommandResult
import com.personal.agent.core.modules.HealthStatus
import com.personal.agent.core.modules.ModuleConfig
import com.personal.agent.core.modules.ModuleContext
import com.personal.agent.core.modules.ModuleHealth

private const val TAG = "VisionEngine"

/**
 * Phase 6 — Vision / OCR Engine [AgentModule].
 *
 * Controls screen capture and OCR pipeline.
 * MediaProjection requires an Intent result from the user — stored in
 * [VisionEngineState] by the foreground service after the user grants permission.
 *
 * Commands handled:
 * - capture_screen   — take screenshot + OCR + classify + persist
 * - ocr_text         — return OCR text of current screen (no persist)
 * - get_screen_type  — classify current screen from pkg + tree hints
 * - get_stats        — return counters
 */
class VisionEngine : AgentModule {

    override val id: String = MODULE_ID
    override val version: Int = 1

    private lateinit var ctx: ModuleContext
    private var ocr: OcrProcessor? = null
    private var screenRecord: ScreenRecord? = null
    private var captureCount: Long = 0L
    private var ocrCount: Long = 0L
    private var lastErrorCode: String? = null
    private var startedAt: Long? = null

    override suspend fun initialize(context: ModuleContext) {
        ctx = context
        Log.i(TAG, "VisionEngine initialized")
    }

    override suspend fun start(config: ModuleConfig) {
        ocr = OcrProcessor()
        screenRecord = ScreenRecord(
            db             = ctx.db,
            ocr            = ocr!!,
            captureManager = VisionEngineState.captureManager
        )
        VisionEngineState.enabled = true
        startedAt = System.currentTimeMillis()
        lastErrorCode = null
        Log.i(TAG, "VisionEngine started (capture=${VisionEngineState.captureManager?.isReady})")
        ctx.logger.info(id, "engine_started", "Vision engine enabled")
    }

    override suspend fun stop(reason: String) {
        VisionEngineState.enabled = false
        ocr?.close()
        ocr = null
        screenRecord = null
        Log.i(TAG, "VisionEngine stopped: $reason")
        ctx.logger.info(id, "engine_stopped", reason)
    }

    override suspend fun applyConfig(config: ModuleConfig) {
        if (!config.enabled && VisionEngineState.enabled) stop("disabled_by_config")
        else if (config.enabled && !VisionEngineState.enabled) start(config)
    }

    override suspend fun health(): ModuleHealth {
        val status = when {
            lastErrorCode != null         -> HealthStatus.DEGRADED
            !VisionEngineState.enabled    -> HealthStatus.STOPPED
            else                          -> HealthStatus.HEALTHY
        }
        return ModuleHealth(
            id            = id,
            status        = status,
            lastSuccessAt = startedAt,
            lastErrorCode = lastErrorCode,
            queueDepth    = 0
        )
    }

    override suspend fun handleCommand(command: AgentCommand): CommandResult {
        if (!VisionEngineState.enabled) {
            return CommandResult(command.id, false, errorCode = "engine_disabled")
        }

        return when (command.type) {
            "capture_screen" -> {
                val record = screenRecord
                    ?: return CommandResult(command.id, false, errorCode = "engine_not_started")

                try {
                    val snapshot = record.record()
                    if (snapshot != null) {
                        captureCount++
                        ocrCount++
                        ctx.logger.info(id, "capture_ok",
                            "${snapshot.classification.screenType} pkg=${snapshot.packageName}")
                        CommandResult(
                            commandId = command.id,
                            success   = true,
                            data      = mapOf(
                                "screenId"    to snapshot.screenId,
                                "screenType"  to snapshot.classification.screenType.name.lowercase(),
                                "packageName" to (snapshot.packageName ?: ""),
                                "ocrBlocks"   to snapshot.ocrResult.blocks.size,
                                "confidence"  to snapshot.classification.confidence
                            )
                        )
                    } else {
                        lastErrorCode = "record_null"
                        CommandResult(command.id, false, errorCode = "capture_failed")
                    }
                } catch (e: Exception) {
                    lastErrorCode = e.javaClass.simpleName
                    Log.e(TAG, "capture_screen error: ${e.message}")
                    CommandResult(command.id, false, errorCode = "exception",
                        data = mapOf("error" to (e.message ?: "unknown")))
                }
            }

            "ocr_text" -> {
                val ocrProc = ocr
                    ?: return CommandResult(command.id, false, errorCode = "ocr_not_ready")

                val bitmap = try {
                    VisionEngineState.captureManager?.capture()
                } catch (e: Exception) {
                    null
                }

                if (bitmap == null) {
                    // Fallback: return accessibility tree text
                    val service = com.personal.agent.accessibility.AccessibilityEngineState.service
                    val root = service?.rootInActiveWindow
                    val snapshot = if (root != null) {
                        com.personal.agent.accessibility.AccessibilityTreeBuilder.build(root)
                            .also { root.recycle() }
                    } else null
                    val text = snapshot?.let {
                        com.personal.agent.accessibility.AccessibilityTreeBuilder
                            .flatten(it)
                            .mapNotNull { n -> n.text }
                            .joinToString(" ")
                    } ?: ""
                    return CommandResult(command.id, true,
                        data = mapOf("text" to text, "source" to "accessibility_tree"))
                }

                val result = try {
                    ocrProc.process(bitmap).also { bitmap.recycle() }
                } catch (e: Exception) {
                    bitmap.recycle()
                    return CommandResult(command.id, false, errorCode = "ocr_failed",
                        data = mapOf("error" to (e.message ?: "")))
                }
                ocrCount++
                CommandResult(
                    commandId = command.id,
                    success   = true,
                    data      = mapOf(
                        "text"   to result.fullText,
                        "blocks" to result.blocks.size,
                        "source" to "mlkit"
                    )
                )
            }

            "get_screen_type" -> {
                val pkg = com.personal.agent.accessibility.foregroundPackage
                val classification = VisionAnalyzer.classify(
                    packageName = pkg,
                    ocrText     = "",
                    treeHints   = TreeHints()
                )
                CommandResult(
                    commandId = command.id,
                    success   = true,
                    data      = mapOf(
                        "screenType"  to classification.screenType.name.lowercase(),
                        "packageName" to (pkg ?: ""),
                        "confidence"  to classification.confidence
                    )
                )
            }

            "get_stats" -> CommandResult(
                commandId = command.id,
                success   = true,
                data      = mapOf(
                    "enabled"        to VisionEngineState.enabled,
                    "captureReady"   to (VisionEngineState.captureManager?.isReady ?: false),
                    "captureCount"   to captureCount,
                    "ocrCount"       to ocrCount,
                    "lastErrorCode"  to (lastErrorCode ?: "none")
                )
            )

            else -> CommandResult(command.id, false, errorCode = "unknown_command")
        }
    }

    companion object {
        const val MODULE_ID = "vision_engine"
    }
}

/**
 * Shared state between [VisionEngine] and the foreground service.
 */
object VisionEngineState {
    @Volatile var enabled: Boolean = false
    @Volatile var captureManager: ScreenCaptureManager? = null
}
