package com.personal.agent.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import java.security.MessageDigest

/**
 * Normalized notification event — output of all app parsers.
 */
data class NotificationEvent(
    val id: String,                  // deterministic SHA-256 of package+key+timestamp
    val packageName: String,
    val appName: String?,
    val title: String?,
    val body: String?,
    val sender: String?,
    val conversation: String?,
    val timestamp: Long,
    val complete: Boolean = true,    // false if body was truncated / unavailable
    val source: String = "generic"   // parser name that produced this event
)

// ─── Deterministic ID ─────────────────────────────────────────────────────────

fun notificationId(packageName: String, key: String, timestamp: Long): String {
    val raw = "$packageName|$key|$timestamp"
    return MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)
}

// ─── Parser interface ─────────────────────────────────────────────────────────

interface AppNotificationParser {
    /** Package names this parser handles. */
    val packages: Set<String>
    /** Returns a parsed event or null if this parser cannot handle the notification. */
    fun parse(sbn: StatusBarNotification, appName: String?): NotificationEvent?
}

// ─── Helper: extract extras safely ───────────────────────────────────────────

internal fun StatusBarNotification.extras() = notification?.extras
internal fun StatusBarNotification.extraText(key: String): String? =
    extras()?.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }

// ─── Generic fallback parser ──────────────────────────────────────────────────

class GenericParser : AppNotificationParser {
    override val packages: Set<String> = emptySet() // matches everything

    override fun parse(sbn: StatusBarNotification, appName: String?): NotificationEvent {
        val title  = sbn.extraText(Notification.EXTRA_TITLE)
        val body   = sbn.extraText(Notification.EXTRA_TEXT)
            ?: sbn.extraText(Notification.EXTRA_BIG_TEXT)
            ?: sbn.extraText(Notification.EXTRA_SUMMARY_TEXT)

        return NotificationEvent(
            id           = notificationId(sbn.packageName, sbn.key ?: sbn.id.toString(), sbn.postTime),
            packageName  = sbn.packageName,
            appName      = appName,
            title        = title,
            body         = body,
            sender       = null,
            conversation = null,
            timestamp    = sbn.postTime,
            source       = "generic"
        )
    }
}

// ─── WhatsApp parser ──────────────────────────────────────────────────────────

class WhatsAppParser : AppNotificationParser {
    override val packages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    override fun parse(sbn: StatusBarNotification, appName: String?): NotificationEvent? {
        // WhatsApp uses MessagingStyle when available
        val extras = sbn.extras() ?: return null

        // Try MessagingStyle messages first (most complete)
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val lastMsg = messages?.lastOrNull()

        val sender = if (lastMsg != null) {
            try {
                val cls = lastMsg.javaClass
                cls.getDeclaredMethod("getSender").also { it.isAccessible = true }
                    .invoke(lastMsg)?.toString()
            } catch (_: Exception) { null }
        } else {
            sbn.extraText(Notification.EXTRA_TITLE)
        }

        val body = if (lastMsg != null) {
            try {
                val cls = lastMsg.javaClass
                cls.getDeclaredMethod("getText").also { it.isAccessible = true }
                    .invoke(lastMsg)?.toString()
            } catch (_: Exception) { sbn.extraText(Notification.EXTRA_TEXT) }
        } else {
            sbn.extraText(Notification.EXTRA_TEXT)
                ?: sbn.extraText(Notification.EXTRA_BIG_TEXT)
        }

        val conversation = sbn.extraText(Notification.EXTRA_CONVERSATION_TITLE)
            ?: sbn.extraText(Notification.EXTRA_TITLE)

        return NotificationEvent(
            id           = notificationId(sbn.packageName, sbn.key ?: sbn.id.toString(), sbn.postTime),
            packageName  = sbn.packageName,
            appName      = appName ?: "WhatsApp",
            title        = sbn.extraText(Notification.EXTRA_TITLE),
            body         = body,
            sender       = sender,
            conversation = conversation,
            timestamp    = sbn.postTime,
            source       = "whatsapp"
        )
    }
}

// ─── Telegram parser ──────────────────────────────────────────────────────────

class TelegramParser : AppNotificationParser {
    override val packages = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram"
    )

    override fun parse(sbn: StatusBarNotification, appName: String?): NotificationEvent? {
        val title  = sbn.extraText(Notification.EXTRA_TITLE) ?: return null
        val body   = sbn.extraText(Notification.EXTRA_TEXT)
            ?: sbn.extraText(Notification.EXTRA_BIG_TEXT)

        // Telegram: title = "Sender name" or "Group: Sender"
        val (conversation, sender) = if (title.contains(": ")) {
            val parts = title.split(": ", limit = 2)
            parts[0] to parts[1]
        } else {
            title to title
        }

        return NotificationEvent(
            id           = notificationId(sbn.packageName, sbn.key ?: sbn.id.toString(), sbn.postTime),
            packageName  = sbn.packageName,
            appName      = appName ?: "Telegram",
            title        = title,
            body         = body,
            sender       = sender,
            conversation = conversation,
            timestamp    = sbn.postTime,
            source       = "telegram"
        )
    }
}

// ─── Gmail parser ─────────────────────────────────────────────────────────────

class GmailParser : AppNotificationParser {
    override val packages = setOf("com.google.android.gm")

    override fun parse(sbn: StatusBarNotification, appName: String?): NotificationEvent? {
        val title  = sbn.extraText(Notification.EXTRA_TITLE) ?: return null
        val body   = sbn.extraText(Notification.EXTRA_TEXT)
            ?: sbn.extraText(Notification.EXTRA_BIG_TEXT)
        val subText = sbn.extraText(Notification.EXTRA_SUB_TEXT)

        return NotificationEvent(
            id           = notificationId(sbn.packageName, sbn.key ?: sbn.id.toString(), sbn.postTime),
            packageName  = sbn.packageName,
            appName      = appName ?: "Gmail",
            title        = title,
            body         = body,
            sender       = title,         // Gmail title = sender name
            conversation = subText,       // subText = account/label
            timestamp    = sbn.postTime,
            source       = "gmail"
        )
    }
}

// ─── SMS parser ───────────────────────────────────────────────────────────────

class SmsParser : AppNotificationParser {
    override val packages = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.oneplus.mms"
    )

    override fun parse(sbn: StatusBarNotification, appName: String?): NotificationEvent? {
        val title  = sbn.extraText(Notification.EXTRA_TITLE) ?: return null
        val body   = sbn.extraText(Notification.EXTRA_TEXT)
            ?: sbn.extraText(Notification.EXTRA_BIG_TEXT)

        return NotificationEvent(
            id           = notificationId(sbn.packageName, sbn.key ?: sbn.id.toString(), sbn.postTime),
            packageName  = sbn.packageName,
            appName      = appName ?: "SMS",
            title        = title,
            body         = body,
            sender       = title,
            conversation = null,
            timestamp    = sbn.postTime,
            source       = "sms"
        )
    }
}

// ─── NotificationParser dispatcher ───────────────────────────────────────────

/**
 * Selects the correct [AppNotificationParser] for a given package,
 * falls back to [GenericParser] for unknown apps.
 */
class NotificationParser(private val pm: PackageManager) {

    private val parsers: List<AppNotificationParser> = listOf(
        WhatsAppParser(),
        TelegramParser(),
        GmailParser(),
        SmsParser(),
    )
    private val fallback = GenericParser()

    // Apps to ignore — system noise
    private val blocklist = setOf(
        "android",
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.gms",
        "com.android.vending",
    )

    fun shouldProcess(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName in blocklist) return false
        if (sbn.isOngoing) return false          // ongoing (music, navigation) — skip
        val n = sbn.notification ?: return false
        if (n.extras == null) return false
        return true
    }

    fun parse(sbn: StatusBarNotification): NotificationEvent? {
        if (!shouldProcess(sbn)) return null

        val appName = try {
            pm.getApplicationLabel(
                pm.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) { null }

        val parser = parsers.firstOrNull { sbn.packageName in it.packages } ?: fallback
        return try {
            parser.parse(sbn, appName)
        } catch (e: Exception) {
            // Fallback if specialized parser throws
            fallback.parse(sbn, appName)
        }
    }
}
