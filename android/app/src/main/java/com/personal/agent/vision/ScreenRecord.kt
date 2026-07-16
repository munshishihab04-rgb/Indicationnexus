package com.personal.agent.vision

import android.graphics.Bitmap
import android.util.Log
import com.personal.agent.accessibility.AccessibilityEngineState
import com.personal.agent.accessibility.AccessibilityTreeBuilder
import com.personal.agent.accessibility.NodeFinder
import com.personal.agent.core.db.AgentDatabase
import com.personal.agent.core.db.OcrEntity
import com.personal.agent.core.db.ScreenEntity
import com.personal.agent.core.db.VisionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "ScreenRecord"

/**
 * Orchestrates a full screen analysis cycle:
 *   1. Take screenshot via [ScreenCaptureManager] (if available).
 *   2. Run OCR via [OcrProcessor].
 *   3. Classify screen type via [VisionAnalyzer].
 *   4. Persist [ScreenEntity], [OcrEntity] rows, and [VisionEntity] to Room.
 *
 * Returns a [ScreenSnapshot] with all results for use by automation engine.
 */
class ScreenRecord(
    private val db: AgentDatabase,
    private val ocr: OcrProcessor,
    private val captureManager: ScreenCaptureManager?
) {

    /**
     * Runs a full capture-OCR-classify cycle.
     *
     * @param bitmap  Pre-captured bitmap (pass null to capture via [captureManager]).
     * @return        [ScreenSnapshot] with all results, or null on critical failure.
     */
    suspend fun record(bitmap: Bitmap? = null): ScreenSnapshot? = withContext(Dispatchers.IO) {
        val screenId = UUID.randomUUID().toString()
        val now      = System.currentTimeMillis()

        // 1. Accessibility tree hints
        val treeHints = buildTreeHints()
        val service   = AccessibilityEngineState.service
        val foregroundPkg = try {
            service?.rootInActiveWindow?.packageName?.toString()
                ?: com.personal.agent.accessibility.foregroundPackage
        } catch (_: Exception) {
            com.personal.agent.accessibility.foregroundPackage
        }

        // 2. Persist ScreenEntity immediately
        db.screenDao().upsert(
            ScreenEntity(
                id              = screenId,
                packageName     = foregroundPkg,
                className       = null,
                screenType      = null,   // filled after classification
                accessibilityJson = treeHints.toJson(),
                createdAt       = now
            )
        )

        // 3. Capture screenshot (if projection available and policy allows)
        val bmp = bitmap ?: captureManager?.let {
            if (it.isReady) {
                try { it.capture() }
                catch (e: Exception) {
                    Log.e(TAG, "Screenshot capture failed: ${e.message}")
                    null
                }
            } else null
        }

        // 4. OCR
        val ocrResult = if (bmp != null) {
            try { ocr.process(bmp) }
            catch (e: Exception) {
                Log.e(TAG, "OCR failed: ${e.message}")
                OcrResult("", emptyList())
            }
        } else {
            OcrResult("", emptyList())
        }
        bmp?.recycle()

        // 5. Persist OCR blocks
        for (block in ocrResult.blocks) {
            db.ocrDao().upsert(
                OcrEntity(
                    id         = UUID.randomUUID().toString(),
                    screenId   = screenId,
                    text       = block.text,
                    boundsJson = block.boundsJson(),
                    confidence = block.confidence,
                    createdAt  = now
                )
            )
        }

        // 6. Classify screen
        val classification = VisionAnalyzer.classify(
            packageName = foregroundPkg,
            ocrText     = ocrResult.fullText,
            treeHints   = treeHints
        )

        // 7. Persist VisionEntity
        db.visionDao().upsert(
            VisionEntity(
                id         = UUID.randomUUID().toString(),
                screenId   = screenId,
                resultJson = classification.resultJson,
                createdAt  = now
            )
        )

        // 8. Update ScreenEntity with screen type
        db.screenDao().upsert(
            ScreenEntity(
                id              = screenId,
                packageName     = foregroundPkg,
                className       = null,
                screenType      = classification.screenType.name.lowercase(),
                accessibilityJson = treeHints.toJson(),
                createdAt       = now
            )
        )

        Log.i(TAG, "Recorded screen $screenId: ${classification.screenType} pkg=$foregroundPkg " +
                "ocr=${ocrResult.blocks.size} blocks")

        ScreenSnapshot(
            screenId       = screenId,
            packageName    = foregroundPkg,
            classification = classification,
            ocrResult      = ocrResult,
            treeHints      = treeHints,
            capturedAt     = now
        )
    }

    // ─── Tree hints from accessibility ────────────────────────────────────────

    private fun buildTreeHints(): TreeHints {
        return try {
            val service = AccessibilityEngineState.service ?: return TreeHints()
            val root = service.rootInActiveWindow ?: return TreeHints()
            val snapshot = AccessibilityTreeBuilder.build(root)
            root.recycle()
            if (snapshot == null) return TreeHints()

            val flat = AccessibilityTreeBuilder.flatten(snapshot)
            TreeHints(
                editableCount  = flat.count { it.editable },
                clickableCount = flat.count { it.clickable },
                hasScrollable  = flat.any { it.scrollable },
                hasMedia       = flat.any {
                    it.className?.contains("video", ignoreCase = true) == true ||
                    it.className?.contains("media", ignoreCase = true) == true
                }
            )
        } catch (_: Exception) { TreeHints() }
    }

    private fun TreeHints.toJson() =
        """{"editable":$editableCount,"clickable":$clickableCount,"scrollable":$hasScrollable,"media":$hasMedia}"""
}

data class ScreenSnapshot(
    val screenId:       String,
    val packageName:    String?,
    val classification: ScreenClassification,
    val ocrResult:      OcrResult,
    val treeHints:      TreeHints,
    val capturedAt:     Long
)
