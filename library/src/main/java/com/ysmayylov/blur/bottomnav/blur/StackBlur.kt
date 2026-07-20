package com.ysmayylov.blur.bottomnav.blur

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * A hand-written implementation of Mario Klingemann's **Stack Blur** algorithm.
 *
 * Stack Blur produces results visually close to a Gaussian blur but is dramatically faster because
 * it processes each row/column with an incrementally updated "stack" of samples instead of a full
 * convolution. This is the default CPU fallback for devices below Android 12.
 *
 * The algorithm operates in-place on an ARGB_8888 bitmap: it reads the pixels once, blurs the R/G/B
 * channels horizontally then vertically, and writes them back. The original alpha is preserved.
 */
class StackBlur : BlurEngine {

    override val maxRadius: Float = 25f
    override val downScaleFactor: Float = 8f
    override val name: String = "StackBlur"

    override fun blur(input: Bitmap, radius: Float): Bitmap {
        val r = radius.toInt().coerceIn(1, maxRadius.toInt())
        // Work on a mutable ARGB_8888 copy if necessary.
        val bitmap = if (input.isMutable && input.config == Bitmap.Config.ARGB_8888) {
            input
        } else {
            input.copy(Bitmap.Config.ARGB_8888, true)
        }

        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return bitmap

        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = r + r + 1

        val rArr = IntArray(wh)
        val gArr = IntArray(wh)
        val bArr = IntArray(wh)
        val vmin = IntArray(maxOf(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (i in 0 until 256 * divsum) dv[i] = i / divsum

        val stack = Array(div) { IntArray(3) }
        val r1 = r + 1

        var yw = 0
        var yi = 0

        // ---- Horizontal pass -------------------------------------------------
        for (y in 0 until h) {
            var rinsum = 0; var ginsum = 0; var binsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0

            for (i in -r..r) {
                val p = pix[yi + minOf(wm, maxOf(i, 0))]
                val sir = stack[i + r]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                val rbs = r1 - abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
            }

            var stackpointer = r
            for (x in 0 until w) {
                rArr[yi] = dv[rsum]
                gArr[yi] = dv[gsum]
                bArr[yi] = dv[bsum]

                rsum -= routsum; gsum -= goutsum; bsum -= boutsum

                var stackstart = stackpointer - r + div
                var sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

                if (y == 0) vmin[x] = minOf(x + r + 1, wm)
                val p = pix[yw + vmin[x]]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = (p and 0x0000ff)
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]

                yi++
            }
            yw += w
        }

        // ---- Vertical pass ---------------------------------------------------
        for (x in 0 until w) {
            var rinsum = 0; var ginsum = 0; var binsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0

            var yp = -r * w
            for (i in -r..r) {
                yi = maxOf(0, yp) + x
                val sir = stack[i + r]
                sir[0] = rArr[yi]; sir[1] = gArr[yi]; sir[2] = bArr[yi]
                val rbs = r1 - abs(i)
                rsum += rArr[yi] * rbs
                gsum += gArr[yi] * rbs
                bsum += bArr[yi] * rbs
                if (i > 0) { rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2] }
                else { routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2] }
                if (i < hm) yp += w
            }

            yi = x
            var stackpointer = r
            for (y in 0 until h) {
                pix[yi] = (0xff000000.toInt() and pix[yi]) or
                    (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum; gsum -= goutsum; bsum -= boutsum

                val stackstart = stackpointer - r + div
                var sir = stack[stackstart % div]
                routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]

                if (x == 0) vmin[y] = minOf(y + r1, hm) * w
                val p = x + vmin[y]
                sir[0] = rArr[p]; sir[1] = gArr[p]; sir[2] = bArr[p]
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
                rsum += rinsum; gsum += ginsum; bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
                rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    override fun release() { /* Pure-CPU engine holds no native resources. */ }
}
