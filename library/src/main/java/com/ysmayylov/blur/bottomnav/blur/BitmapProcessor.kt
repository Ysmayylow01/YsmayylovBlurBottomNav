package com.ysmayylov.blur.bottomnav.blur

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * Small utility responsible for the bitmap lifecycle used by the blur pipeline:
 * allocating/reusing the down-scaled capture buffer, wiping it between frames, and recycling it on
 * teardown. Keeping this logic in one place avoids leaks and needless re-allocations per frame.
 */
internal class BitmapProcessor {

    /** The bitmap the surrounding content is captured into (already down-scaled). */
    var captureBitmap: Bitmap? = null
        private set

    /** Reusable canvas bound to [captureBitmap]. */
    var captureCanvas: Canvas? = null
        private set

    /** Down-scaled dimensions currently in use. */
    var scaledWidth = 0
        private set
    var scaledHeight = 0
        private set

    /**
     * Ensures a capture buffer exists that matches the requested down-scaled size, re-allocating
     * only when the size actually changes (e.g. after a rotation or a layout change).
     *
     * @return true if a usable buffer is ready, false if the requested size was degenerate.
     */
    fun prepare(viewWidth: Int, viewHeight: Int, downScaleFactor: Float): Boolean {
        if (viewWidth <= 0 || viewHeight <= 0) return false

        val targetW = (viewWidth / downScaleFactor).toInt().coerceAtLeast(1)
        val targetH = (viewHeight / downScaleFactor).toInt().coerceAtLeast(1)

        val existing = captureBitmap
        if (existing != null && !existing.isRecycled &&
            targetW == scaledWidth && targetH == scaledHeight
        ) {
            return true
        }

        recycle()
        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        captureBitmap = bitmap
        captureCanvas = Canvas(bitmap)
        scaledWidth = targetW
        scaledHeight = targetH
        return true
    }

    /** Clears the capture buffer to fully transparent before the next capture pass. */
    fun clear() {
        captureBitmap?.eraseColor(android.graphics.Color.TRANSPARENT)
    }

    /** Releases the capture buffer. Safe to call repeatedly. */
    fun recycle() {
        captureCanvas = null
        captureBitmap?.let { if (!it.isRecycled) it.recycle() }
        captureBitmap = null
        scaledWidth = 0
        scaledHeight = 0
    }
}
