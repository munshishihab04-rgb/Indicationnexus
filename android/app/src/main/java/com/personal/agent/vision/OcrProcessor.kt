package com.personal.agent.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "OcrProcessor"

/**
 * Runs ML Kit on-device text recognition on a [Bitmap].
 * Returns a list of [OcrBlock] — one per recognised text block.
 */
class OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Processes [bitmap] and returns recognised text blocks.
     * Suspends until ML Kit completes (on its own thread pool).
     */
    suspend fun process(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = visionText.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        OcrBlock(
                            text       = line.text,
                            confidence = line.confidence ?: 1f,
                            bounds     = line.boundingBox ?: Rect(0, 0, 0, 0)
                        )
                    }
                }
                val fullText = visionText.text
                Log.d(TAG, "OCR complete: ${blocks.size} lines, ${fullText.length} chars")
                cont.resume(OcrResult(fullText = fullText, blocks = blocks))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failed: ${e.message}")
                if (cont.isActive) cont.resumeWithException(e)
            }
        cont.invokeOnCancellation { recognizer.close() }
    }

    fun close() {
        recognizer.close()
    }
}

data class OcrBlock(
    val text:       String,
    val confidence: Float,
    val bounds:     Rect
) {
    fun boundsJson(): String =
        """{"l":${bounds.left},"t":${bounds.top},"r":${bounds.right},"b":${bounds.bottom}}"""
}

data class OcrResult(
    val fullText: String,
    val blocks:   List<OcrBlock>
) {
    val isEmpty: Boolean get() = fullText.isBlank()
}
