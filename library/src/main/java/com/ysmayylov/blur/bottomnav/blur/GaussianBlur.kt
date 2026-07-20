package com.ysmayylov.blur.bottomnav.blur

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A from-scratch **separable Gaussian blur**.
 *
 * A true 2D Gaussian kernel is separable, so instead of an O(r²) convolution per pixel we run two
 * O(r) passes: horizontal then vertical. This keeps it reasonably fast while producing a
 * mathematically correct Gaussian falloff.
 *
 * It is offered as an alternative to [StackBlur] for callers who prefer the smoother, more accurate
 * (but slightly heavier) Gaussian look. Alpha is preserved.
 */
class GaussianBlur : BlurEngine {

    override val maxRadius: Float = 25f
    override val downScaleFactor: Float = 8f
    override val name: String = "GaussianBlur"

    override fun blur(input: Bitmap, radius: Float): Bitmap {
        val r = radius.toInt().coerceIn(1, maxRadius.toInt())
        val bitmap = if (input.isMutable && input.config == Bitmap.Config.ARGB_8888) {
            input
        } else {
            input.copy(Bitmap.Config.ARGB_8888, true)
        }

        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        val src = IntArray(w * h)
        val tmp = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)

        val kernel = buildKernel(r)

        // Horizontal pass: src -> tmp
        convolve(src, tmp, w, h, kernel, horizontal = true)
        // Vertical pass: tmp -> src
        convolve(tmp, src, w, h, kernel, horizontal = false)

        bitmap.setPixels(src, 0, w, 0, 0, w, h)
        return bitmap
    }

    /** Builds a normalized 1D Gaussian kernel for the given radius. */
    private fun buildKernel(radius: Int): FloatArray {
        val sigma = radius / 2f + 0.5f
        val size = radius * 2 + 1
        val kernel = FloatArray(size)
        val twoSigmaSq = 2f * sigma * sigma
        val norm = 1f / sqrt(Math.PI.toFloat() * twoSigmaSq)
        var sum = 0f
        for (i in -radius..radius) {
            val v = norm * exp(-(i * i) / twoSigmaSq)
            kernel[i + radius] = v
            sum += v
        }
        for (i in kernel.indices) kernel[i] /= sum
        return kernel
    }

    private fun convolve(
        input: IntArray,
        output: IntArray,
        w: Int,
        h: Int,
        kernel: FloatArray,
        horizontal: Boolean
    ) {
        val radius = kernel.size / 2
        for (y in 0 until h) {
            for (x in 0 until w) {
                var a = 0f; var red = 0f; var green = 0f; var blue = 0f
                for (k in -radius..radius) {
                    val weight = kernel[k + radius]
                    val sampleIndex = if (horizontal) {
                        y * w + (x + k).coerceIn(0, w - 1)
                    } else {
                        (y + k).coerceIn(0, h - 1) * w + x
                    }
                    val p = input[sampleIndex]
                    a += ((p ushr 24) and 0xff) * weight
                    red += ((p ushr 16) and 0xff) * weight
                    green += ((p ushr 8) and 0xff) * weight
                    blue += (p and 0xff) * weight
                }
                output[y * w + x] = (a.toInt().coerceIn(0, 255) shl 24) or
                    (red.toInt().coerceIn(0, 255) shl 16) or
                    (green.toInt().coerceIn(0, 255) shl 8) or
                    blue.toInt().coerceIn(0, 255)
            }
        }
    }

    override fun release() { /* Pure-CPU engine holds no native resources. */ }
}
