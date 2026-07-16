package com.personal.agent.vision

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "ScreenCaptureManager"
private const val VIRTUAL_DISPLAY_NAME = "agent_capture"

/**
 * Captures screenshots using a [MediaProjection] token.
 *
 * Lifecycle:
 *   1. Call [setup] once with the MediaProjection obtained from the system.
 *   2. Call [capture] to take a screenshot bitmap.
 *   3. Call [release] on service stop.
 *
 * Threading: capture runs on the calling coroutine dispatcher.
 * The ImageReader listener is driven by a separate thread internally.
 */
class ScreenCaptureManager(
    private val windowManager: WindowManager
) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth: Int  = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0

    /**
     * Initialises capture with the given [projection].
     * Must be called before [capture].
     */
    fun setup(projection: MediaProjection) {
        mediaProjection = projection
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth   = metrics.widthPixels
        screenHeight  = metrics.heightPixels
        screenDensity = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, null
        )
        Log.i(TAG, "ScreenCaptureManager ready (${screenWidth}x${screenHeight} @${screenDensity}dpi)")
    }

    /**
     * Captures one frame and returns it as a [Bitmap].
     * Returns null if MediaProjection has not been set up or capture fails.
     * The returned bitmap must be recycled by the caller when no longer needed.
     */
    suspend fun capture(): Bitmap? {
        val reader = imageReader ?: run {
            Log.w(TAG, "capture() called before setup()")
            return null
        }
        return suspendCancellableCoroutine { cont ->
            reader.setOnImageAvailableListener({ r ->
                r.setOnImageAvailableListener(null, null) // one-shot
                var image: Image? = null
                try {
                    image = r.acquireLatestImage()
                    if (image == null) {
                        cont.resume(null)
                        return@setOnImageAvailableListener
                    }
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride   = planes[0].rowStride
                    val rowPadding  = rowStride - pixelStride * screenWidth

                    val bmp = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(buffer)

                    // Crop to exact screen dimensions if padded
                    val result = if (rowPadding != 0)
                        Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight)
                            .also { if (it !== bmp) bmp.recycle() }
                    else bmp

                    cont.resume(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Capture error: ${e.message}")
                    if (cont.isActive) cont.resumeWithException(e)
                } finally {
                    image?.close()
                }
            }, null)
        }
    }

    /** Returns true if MediaProjection is active. */
    val isReady: Boolean get() = mediaProjection != null && virtualDisplay != null

    /** Releases all resources. Call when the projection session ends. */
    fun release() {
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        Log.i(TAG, "ScreenCaptureManager released")
    }
}
