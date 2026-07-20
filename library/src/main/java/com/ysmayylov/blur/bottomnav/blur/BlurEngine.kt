package com.ysmayylov.blur.bottomnav.blur

import android.graphics.Bitmap

/**
 * Contract for every blur implementation shipped with the library.
 *
 * Implementations are intentionally free of any third-party dependency:
 * [RenderEffectBlur] uses the platform [android.graphics.RenderEffect] pipeline on API 31+,
 * while [StackBlur] and [GaussianBlur] are pure-Kotlin CPU algorithms that run everywhere.
 */
interface BlurEngine {

    /** Largest blur radius (in pixels, measured on the down-scaled bitmap) this engine handles well. */
    val maxRadius: Float

    /**
     * Down-scale factor applied to the captured content before blurring.
     * A larger factor means a smaller bitmap and therefore a cheaper (and softer) blur.
     */
    val downScaleFactor: Float

    /**
     * Blurs [input] and returns the blurred result.
     *
     * Implementations may mutate and return [input] in place (CPU engines) or return a brand new
     * bitmap (GPU engine). Callers must therefore treat the returned bitmap as the current frame
     * and not keep long-lived references to the one they passed in.
     *
     * @param radius blur strength in pixels on the down-scaled bitmap; values are coerced internally.
     */
    fun blur(input: Bitmap, radius: Float): Bitmap

    /** Releases any native/GPU resources held by the engine. Safe to call multiple times. */
    fun release()

    /** Human readable engine name, useful for logging / debugging. */
    val name: String
}
