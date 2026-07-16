package com.personal.agent.vision

import android.graphics.Bitmap
import android.util.Log
import com.personal.agent.accessibility.AccessibilityEngineState

private const val TAG = "VisionAnalyzer"

/**
 * Classifies the current screen type using heuristics derived from:
 * 1. The foreground package name (from [AccessibilityEngineState.foregroundPackage]).
 * 2. OCR text content from the screen.
 * 3. Optional: accessibility tree hints (passed as [treeHints]).
 *
 * Screen types (from doc 06):
 *   home, launcher, chat, messaging, email, browser, form, media_player,
 *   camera, settings, notification_shade, unknown
 *
 * Phase 6 uses rule-based classification. Phase 8+ may swap in an ML model.
 */
object VisionAnalyzer {

    fun classify(
        packageName: String?,
        ocrText: String,
        treeHints: TreeHints = TreeHints()
    ): ScreenClassification {

        val pkg = packageName?.lowercase() ?: ""
        val text = ocrText.lowercase()

        val screenType = when {
            // Launcher / Home
            pkg.contains("launcher") || pkg.contains("home") ->
                ScreenType.LAUNCHER

            // Messaging apps
            pkg in MESSAGING_PACKAGES ->
                classifyMessagingScreen(text, treeHints)

            // Email
            pkg in EMAIL_PACKAGES ->
                ScreenType.EMAIL

            // Browser
            pkg in BROWSER_PACKAGES ->
                ScreenType.BROWSER

            // Camera
            pkg.contains("camera") ->
                ScreenType.CAMERA

            // Media player
            pkg in MEDIA_PACKAGES ->
                ScreenType.MEDIA_PLAYER

            // Settings
            pkg.contains("settings") ->
                ScreenType.SETTINGS

            // Form detection via OCR + tree hints
            treeHints.editableCount >= 2 ->
                ScreenType.FORM

            // Chat keywords in OCR
            text.containsAny("type a message", "reply", "send", "sticker") ->
                ScreenType.CHAT

            // Generic
            else -> ScreenType.UNKNOWN
        }

        val confidence = when {
            pkg.isNotBlank() && screenType != ScreenType.UNKNOWN -> 0.85f
            screenType == ScreenType.FORM -> 0.75f
            screenType == ScreenType.CHAT -> 0.70f
            else -> 0.40f
        }

        val keywords = extractKeywords(ocrText)

        Log.d(TAG, "Screen classified: $screenType (confidence=$confidence, pkg=$pkg)")
        return ScreenClassification(
            screenType  = screenType,
            packageName = packageName,
            confidence  = confidence,
            keywords    = keywords,
            resultJson  = buildResultJson(screenType, packageName, confidence, keywords)
        )
    }

    private fun classifyMessagingScreen(text: String, hints: TreeHints): ScreenType =
        when {
            text.containsAny("type a message", "message...", "reply", "send") -> ScreenType.CHAT
            text.containsAny("chats", "messages", "conversations")            -> ScreenType.MESSAGING_LIST
            else                                                               -> ScreenType.CHAT
        }

    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf("the", "a", "is", "in", "on", "at", "to", "of", "and", "or", "for")
        return text.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 3 && it.lowercase() !in stopWords }
            .map { it.lowercase() }
            .distinct()
            .take(20)
    }

    private fun buildResultJson(
        type: ScreenType,
        pkg: String?,
        confidence: Float,
        keywords: List<String>
    ): String {
        val kw = keywords.joinToString(",") { "\"$it\"" }
        return """{"screenType":"$type","packageName":"${pkg ?: ""}","confidence":$confidence,"keywords":[$kw]}"""
    }

    private fun String.containsAny(vararg tokens: String): Boolean =
        tokens.any { this.contains(it, ignoreCase = true) }

    // ─── Package sets ─────────────────────────────────────────────────────────

    private val MESSAGING_PACKAGES = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "org.telegram.messenger", "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "com.facebook.orca",               // Messenger
        "com.instagram.android",
        "com.snapchat.android",
        "com.viber.voip",
        "com.discord",
        "com.google.android.apps.messaging",  // Google Messages
    )

    private val EMAIL_PACKAGES = setOf(
        "com.google.android.gm",
        "com.microsoft.outlook",
        "org.thoughtcrime.securesms",
        "com.android.email",
    )

    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
    )

    private val MEDIA_PACKAGES = setOf(
        "com.spotify.music",
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.google.android.apps.youtube.music",
        "com.amazon.avod.thirdpartyclient",
    )
}

enum class ScreenType {
    LAUNCHER, CHAT, MESSAGING_LIST, EMAIL, BROWSER,
    FORM, CAMERA, MEDIA_PLAYER, SETTINGS, UNKNOWN
}

data class ScreenClassification(
    val screenType:  ScreenType,
    val packageName: String?,
    val confidence:  Float,
    val keywords:    List<String>,
    val resultJson:  String
)

/**
 * Hints extracted from the accessibility tree to improve classification.
 */
data class TreeHints(
    val editableCount:  Int = 0,
    val clickableCount: Int = 0,
    val hasScrollable:  Boolean = false,
    val hasMedia:       Boolean = false
)
