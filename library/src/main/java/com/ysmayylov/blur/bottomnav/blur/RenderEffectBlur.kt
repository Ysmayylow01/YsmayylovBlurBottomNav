package com.ysmayylov.blur.bottomnav.blur

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Hardware-accelerated blur built directly on the platform GPU pipeline available from **Android 12
 * (API 31)**. No RenderScript, no external dependency.
 *
 * The trick: we record the captured bitmap into a [RenderNode], attach a native
 * [RenderEffect.createBlurEffect], then drive that node through a [HardwareRenderer] whose output
 * surface is an [ImageReader]. Reading the resulting [HardwareBuffer] back gives us a fully blurred
 * bitmap produced entirely on the GPU.
 *
 * Any failure (driver quirk, surface loss, etc.) throws, and the controller falls back to
 * [StackBlur] automatically, so callers always get a valid frame.
 */
@RequiresApi(Build.VERSION_CODES.S)
class RenderEffectBlur : BlurEngine {

    override val maxRadius: Float = 25f
    // The GPU handles full resolution cheaply, but a light down-scale keeps capture costs low.
    override val downScaleFactor: Float = 4f
    override val name: String = "RenderEffectBlur"

    private val renderNode = RenderNode("YsmayylovBlurNode")
    private var hardwareRenderer: HardwareRenderer? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0

    override fun blur(input: Bitmap, radius: Float): Bitmap {
        val w = input.width
        val h = input.height
        if (w != width || h != height || imageReader == null) {
            setup(w, h)
        }

        val r = radius.coerceIn(0.1f, 250f)

        val recordingCanvas = renderNode.beginRecording()
        recordingCanvas.drawBitmap(input, 0f, 0f, null)
        renderNode.endRecording()
        renderNode.setRenderEffect(
            RenderEffect.createBlurEffect(r, r, Shader.TileMode.MIRROR)
        )

        val renderer = hardwareRenderer ?: throw IllegalStateException("HardwareRenderer not ready")
        renderer.createRenderRequest()
            .setWaitForPresent(true)
            .syncAndDraw()

        val image = imageReader?.acquireNextImage()
            ?: throw IllegalStateException("No image produced by GPU blur")

        try {
            val buffer: HardwareBuffer = image.hardwareBuffer
                ?: throw IllegalStateException("Null HardwareBuffer")
            buffer.use {
                val hardware = Bitmap.wrapHardwareBuffer(it, null)
                    ?: throw IllegalStateException("wrapHardwareBuffer returned null")
                // Copy off the shared buffer so the next frame can safely reuse it while we draw.
                return hardware.copy(Bitmap.Config.ARGB_8888, false)
            }
        } finally {
            image.close()
        }
    }

    private fun setup(w: Int, h: Int) {
        release()
        width = w
        height = h
        imageReader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, MAX_IMAGES,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        renderNode.setPosition(0, 0, w, h)
        hardwareRenderer = HardwareRenderer().apply {
            setSurface(imageReader!!.surface)
            setContentRoot(renderNode)
        }
    }

    override fun release() {
        hardwareRenderer?.destroy()
        hardwareRenderer = null
        imageReader?.close()
        imageReader = null
        renderNode.setRenderEffect(null)
        renderNode.discardDisplayList()
        width = 0
        height = 0
    }

    private companion object {
        const val MAX_IMAGES = 3
    }
}
