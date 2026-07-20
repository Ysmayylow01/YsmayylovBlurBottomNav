package com.ysmayylov.blur.bottomnav.controller

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import com.ysmayylov.blur.bottomnav.blur.BitmapProcessor
import com.ysmayylov.blur.bottomnav.blur.BlurEngine
import com.ysmayylov.blur.bottomnav.blur.RenderEffectBlur
import com.ysmayylov.blur.bottomnav.blur.StackBlur

/**
 * The default [BlurController]: a "capture-behind" strategy inspired by the classic real-time blur
 * approach but written entirely in-house.
 *
 * On every frame (throttled by [updateInterval]) it:
 *  1. draws the [rootView] hierarchy into a small down-scaled [BitmapProcessor.captureCanvas],
 *     skipping the host view itself (see [draw]) to prevent infinite self-capture;
 *  2. blurs that bitmap with the active [BlurEngine];
 *  3. asks the host to redraw, at which point [draw] paints the blurred bitmap — clipped to the
 *     host's rounded-rect via a [BitmapShader] — as the glass backdrop.
 *
 * The GPU [RenderEffectBlur] is used on API 31+ with an automatic fall back to the CPU [StackBlur]
 * if anything goes wrong, so the component never fails to render.
 */
class PreDrawBlurController(
    private val blurView: View,
    private val rootView: View,
    primaryEngine: BlurEngine
) : BlurController {

    private var engine: BlurEngine = primaryEngine
    private val fallbackEngine: BlurEngine by lazy { StackBlur() }
    private var usingFallback = false

    private val processor = BitmapProcessor()
    private var blurredBitmap: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shaderMatrix = Matrix()
    private val rect = RectF()

    private var blurEnabled = true
    private var blurRadiusPx = 25f
    private var updateInterval = 0L
    private var lastUpdate = 0L
    private var forceUpdate = true

    private val locations = IntArray(2)
    private val rootLocations = IntArray(2)

    /** Corner radius the host wants applied to the glass backdrop, in px. Set by the View. */
    var cornerRadius: Float = 0f

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (blurEnabled) updateBlur()
        true
    }

    override fun attach() {
        blurView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    override fun detach() {
        blurView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        processor.recycle()
        blurredBitmap = null
        engine.release()
        if (usingFallback) fallbackEngine.release()
    }

    override fun setBlurEnabled(enabled: Boolean) {
        blurEnabled = enabled
        forceUpdate = true
    }

    override fun setBlurRadius(radiusPx: Float) {
        blurRadiusPx = radiusPx
        forceUpdate = true
    }

    override fun setUpdateInterval(intervalMs: Long) {
        updateInterval = intervalMs.coerceAtLeast(0L)
    }

    override fun invalidateBlur() {
        forceUpdate = true
    }

    /**
     * Captures + blurs the content behind the host. Runs on the UI thread inside the pre-draw pass;
     * it is intentionally kept cheap by working on a heavily down-scaled bitmap so it does not block.
     */
    private fun updateBlur() {
        if (blurView.width == 0 || blurView.height == 0) return

        val now = SystemClock.uptimeMillis()
        if (!forceUpdate && updateInterval > 0 && now - lastUpdate < updateInterval) return

        val activeEngine = if (usingFallback) fallbackEngine else engine
        if (!processor.prepare(blurView.width, blurView.height, activeEngine.downScaleFactor)) return

        val canvas = processor.captureCanvas ?: return
        val captureBitmap = processor.captureBitmap ?: return

        processor.clear()

        // Map the root view's pixels into the down-scaled capture canvas so the region directly
        // behind the host lands at (0,0) of the buffer.
        blurView.getLocationOnScreen(locations)
        rootView.getLocationOnScreen(rootLocations)
        val relativeLeft = (locations[0] - rootLocations[0]).toFloat()
        val relativeTop = (locations[1] - rootLocations[1]).toFloat()

        val scaleW = blurView.width.toFloat() / processor.scaledWidth
        val scaleH = blurView.height.toFloat() / processor.scaledHeight

        canvas.save()
        canvas.translate(-relativeLeft / scaleW, -relativeTop / scaleH)
        canvas.scale(1f / scaleW, 1f / scaleH)
        try {
            rootView.draw(canvas) // host view skips itself via draw() returning false
        } catch (t: Throwable) {
            canvas.restore()
            return
        }
        canvas.restore()

        blurredBitmap = try {
            activeEngine.blur(captureBitmap, effectiveRadius(activeEngine))
        } catch (t: Throwable) {
            // GPU path failed on this device — permanently drop to the CPU engine.
            switchToFallback()
            try {
                fallbackEngine.blur(captureBitmap, effectiveRadius(fallbackEngine))
            } catch (inner: Throwable) {
                null
            }
        }

        lastUpdate = now
        forceUpdate = false
        blurView.invalidate()
    }

    private fun effectiveRadius(activeEngine: BlurEngine): Float {
        // Radius is expressed in real pixels; scale it into the down-scaled bitmap space.
        return (blurRadiusPx / activeEngine.downScaleFactor)
            .coerceIn(1f, activeEngine.maxRadius)
    }

    private fun switchToFallback() {
        if (usingFallback) return
        usingFallback = true
        engine.release()
    }

    override fun draw(canvas: Canvas): Boolean {
        if (!blurEnabled) return true

        // Detect the internal capture pass: our capture canvas is bound to the capture bitmap.
        val captureCanvas = processor.captureCanvas
        if (captureCanvas === canvas) {
            // We are being drawn into our own buffer — skip to avoid capturing ourselves.
            return false
        }

        val bitmap = blurredBitmap ?: return true
        if (bitmap.isRecycled) return true

        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        shaderMatrix.reset()
        shaderMatrix.setScale(
            blurView.width.toFloat() / bitmap.width,
            blurView.height.toFloat() / bitmap.height
        )
        shader.setLocalMatrix(shaderMatrix)
        paint.shader = shader

        rect.set(0f, 0f, blurView.width.toFloat(), blurView.height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.shader = null

        return true
    }

    companion object {
        /** Picks the best engine for the running platform. */
        fun defaultEngine(): BlurEngine =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    RenderEffectBlur()
                } catch (t: Throwable) {
                    StackBlur()
                }
            } else {
                StackBlur()
            }
    }
}
