package com.personal.agent.automation

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personal.agent.accessibility.AccessibilityEngineState
import com.personal.agent.accessibility.ActionCommand
import com.personal.agent.accessibility.ActionType as A11yActionType
import com.personal.agent.core.AgentApp
import com.personal.agent.vision.VisionEngineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "ActionDispatcher"

/**
 * Translates [WorkflowAction] into concrete calls to other engines.
 * Each dispatch returns an [ActionOutcome] with success flag, optional value,
 * and a short human-readable message for the step log.
 */
class ActionDispatcher(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun dispatch(action: WorkflowAction, ctx: RunContext): ActionOutcome =
        withContext(Dispatchers.IO) {
            try {
                when (action.type) {
                    // ─── Accessibility actions ─────────────────────────────────────────
                    ActionType.CLICK, ActionType.LONG_CLICK,
                    ActionType.TYPE, ActionType.CLEAR_TEXT,
                    ActionType.SCROLL_FORWARD, ActionType.SCROLL_BACKWARD, ActionType.SWIPE,
                    ActionType.BACK, ActionType.HOME, ActionType.RECENTS,
                    ActionType.NOTIFICATIONS, ActionType.QUICK_SETTINGS,
                    ActionType.WAIT_ELEMENT, ActionType.WAIT_TEXT, ActionType.FOCUS -> {
                        val executor = AccessibilityEngineState.executor
                            ?: return@withContext ActionOutcome.fail("accessibility_service_not_connected")

                        val a11yType = mapToA11yActionType(action.type)
                            ?: return@withContext ActionOutcome.fail("unknown_a11y_type")

                        val cmd = ActionCommand(
                            type               = a11yType,
                            targetText         = ctx.resolve(action.targetText),
                            targetViewId       = ctx.resolve(action.targetViewId),
                            targetClass        = action.targetClass,
                            targetDescription  = ctx.resolve(action.targetDescription),
                            text               = ctx.resolve(action.text),
                            timeoutMs          = action.timeoutMs,
                            scrollDirection    = action.scrollDirection,
                            swipeStartX        = action.swipeStartX,
                            swipeStartY        = action.swipeStartY,
                            swipeEndX          = action.swipeEndX,
                            swipeEndY          = action.swipeEndY,
                            swipeDurationMs    = 300,
                            useFallbackGesture = action.useFallbackGesture
                        )
                        val result = withContext(Dispatchers.Main) { executor.execute(cmd) }
                        if (result.success) ActionOutcome.ok(result.message)
                        else ActionOutcome.fail(result.errorCode ?: "a11y_error", result.message)
                    }

                    // ─── Vision / OCR ──────────────────────────────────────────────────
                    ActionType.CAPTURE_SCREEN -> {
                        val captureManager = VisionEngineState.captureManager
                        if (captureManager?.isReady == true) {
                            val bmp = try { captureManager.capture() } catch (_: Exception) { null }
                            bmp?.recycle()
                            ActionOutcome.ok("screen captured")
                        } else {
                            ActionOutcome.ok("capture skipped (no projection)")
                        }
                    }

                    ActionType.OCR_TEXT -> {
                        val service = AccessibilityEngineState.service
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
                        ctx.vars["ocr_text"] = text
                        ActionOutcome.ok("ocr_text captured (${text.length} chars)", value = text)
                    }

                    // ─── Wait ──────────────────────────────────────────────────────────
                    ActionType.WAIT_MS -> {
                        val ms = action.waitMs.coerceIn(0, 60_000)
                        delay(ms)
                        ActionOutcome.ok("waited ${ms}ms")
                    }

                    // ─── App / URL ─────────────────────────────────────────────────────
                    ActionType.LAUNCH_APP -> {
                        val pkg = ctx.resolve(action.packageName)
                            ?: return@withContext ActionOutcome.fail("no_package")
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                ?: return@withContext ActionOutcome.fail("app_not_found")
                            context.startActivity(intent)
                            delay(500)
                            ActionOutcome.ok("launched $pkg")
                        } catch (e: Exception) {
                            ActionOutcome.fail("launch_failed", e.message ?: "")
                        }
                    }

                    ActionType.OPEN_URL -> {
                        val url = ctx.resolve(action.url)
                            ?: return@withContext ActionOutcome.fail("no_url")
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            context.startActivity(intent)
                            ActionOutcome.ok("opened $url")
                        } catch (e: Exception) {
                            ActionOutcome.fail("open_url_failed", e.message ?: "")
                        }
                    }

                    // ─── HTTP ──────────────────────────────────────────────────────────
                    ActionType.HTTP_POST -> {
                        val url  = ctx.resolve(action.httpUrl)
                            ?: return@withContext ActionOutcome.fail("no_url")
                        val body = ctx.resolve(action.httpBodyJson) ?: "{}"
                        try {
                            val req = Request.Builder()
                                .url(url)
                                .post(body.toRequestBody("application/json".toMediaType()))
                                .build()
                            http.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) ActionOutcome.ok("HTTP ${resp.code}")
                                else ActionOutcome.fail("http_${resp.code}", "HTTP error")
                            }
                        } catch (e: Exception) {
                            ActionOutcome.fail("http_error", e.message ?: "")
                        }
                    }

                    // ─── Notification ──────────────────────────────────────────────────
                    ActionType.SEND_NOTIFICATION -> {
                        val title = ctx.resolve(action.notificationTitle) ?: "Agent"
                        val body  = ctx.resolve(action.notificationBody) ?: ""
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                                    as NotificationManager
                        val notif = NotificationCompat.Builder(context, AgentApp.CHANNEL_AGENT)
                            .setContentTitle(title)
                            .setContentText(body)
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setAutoCancel(true)
                            .build()
                        nm.notify(System.currentTimeMillis().toInt(), notif)
                        ActionOutcome.ok("notification sent")
                    }

                    // ─── Flow control ──────────────────────────────────────────────────
                    ActionType.IF_TEXT_CONTAINS -> {
                        val query = ctx.resolve(action.condition) ?: ""
                        val text  = ctx.vars["ocr_text"]?.toString() ?: ""
                        val matches = text.contains(query, ignoreCase = true)
                        ctx.vars["if_result"] = matches
                        ActionOutcome.ok("if_text_contains=$matches", value = matches.toString())
                    }

                    ActionType.REPEAT -> {
                        // Placeholder: repeat count is handled by WorkflowRunner
                        ActionOutcome.ok("repeat=${action.repeatCount}")
                    }

                    // ─── Log ───────────────────────────────────────────────────────────
                    ActionType.LOG_EVENT -> {
                        val msg = ctx.resolve(action.logMessage) ?: ""
                        Log.i(TAG, "Workflow log [${ctx.runId}]: $msg")
                        ActionOutcome.ok("logged: $msg")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Dispatch error: ${e.message}")
                ActionOutcome.fail("exception", e.message ?: "unknown")
            }
        }

    // ─── Map WorkflowDSL ActionType → Accessibility ActionType ────────────────

    private fun mapToA11yActionType(type: ActionType): A11yActionType? = when (type) {
        ActionType.CLICK             -> A11yActionType.CLICK
        ActionType.LONG_CLICK        -> A11yActionType.LONG_CLICK
        ActionType.TYPE              -> A11yActionType.TYPE
        ActionType.CLEAR_TEXT        -> A11yActionType.CLEAR_TEXT
        ActionType.SCROLL_FORWARD    -> A11yActionType.SCROLL_FORWARD
        ActionType.SCROLL_BACKWARD   -> A11yActionType.SCROLL_BACKWARD
        ActionType.SWIPE             -> A11yActionType.SWIPE
        ActionType.BACK              -> A11yActionType.BACK
        ActionType.HOME              -> A11yActionType.HOME
        ActionType.RECENTS           -> A11yActionType.RECENTS
        ActionType.NOTIFICATIONS     -> A11yActionType.NOTIFICATIONS
        ActionType.QUICK_SETTINGS    -> A11yActionType.QUICK_SETTINGS
        ActionType.WAIT_ELEMENT      -> A11yActionType.WAIT_ELEMENT
        ActionType.WAIT_TEXT         -> A11yActionType.WAIT_TEXT
        ActionType.FOCUS             -> A11yActionType.FOCUS
        else                         -> null
    }
}

data class ActionOutcome(
    val success: Boolean,
    val message: String  = "",
    val errorCode: String? = null,
    val value: String?   = null
) {
    companion object {
        fun ok(msg: String, value: String? = null)     = ActionOutcome(true,  msg, null, value)
        fun fail(code: String, msg: String = "")       = ActionOutcome(false, msg, code)
    }
}
