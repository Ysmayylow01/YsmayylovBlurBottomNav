package com.ysmayylov.blur.bottomnav.controller

import android.graphics.Canvas

/**
 * Abstraction over "how the blur behind the view is produced and drawn".
 *
 * Splitting this out of the View keeps the View focused on navigation concerns and lets the blur
 * strategy evolve independently (SOLID: single responsibility + open/closed).
 */
interface BlurController {

    /**
     * Called from the host View's [android.view.View.draw]. Draws the current blurred backdrop onto
     * [canvas] and returns whether the host should proceed with its normal drawing.
     *
     * Returning `false` signals the "internal capture" pass (the controller is drawing the view
     * hierarchy into its own buffer) and the host must skip drawing itself to avoid capturing itself
     * recursively.
     */
    fun draw(canvas: Canvas): Boolean

    /** Enables/disables blur at runtime. When disabled, [draw] becomes a no-op returning true. */
    fun setBlurEnabled(enabled: Boolean)

    /** Updates the blur radius (in dp-derived pixels) at runtime. */
    fun setBlurRadius(radiusPx: Float)

    /** Minimum time between blur recomputations, in ms. 0 recomputes every frame. */
    fun setUpdateInterval(intervalMs: Long)

    /** Attaches lifecycle observers (pre-draw listener, etc.). Called from onAttachedToWindow. */
    fun attach()

    /** Detaches observers and releases all bitmap/GPU resources. Called from onDetachedFromWindow. */
    fun detach()

    /** Forces a one-off blur refresh on the next frame. */
    fun invalidateBlur()
}
