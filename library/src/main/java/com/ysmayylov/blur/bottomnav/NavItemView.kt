package com.ysmayylov.blur.bottomnav

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ysmayylov.blur.bottomnav.util.dpToPx
import kotlin.math.max

/**
 * The View for one [NavItem]: an icon above an optional label, with a badge overlay.
 *
 * It owns its own selected/unselected transition — a spring-like scale on the icon plus an
 * [ArgbEvaluator] color blend between the configured selected/unselected colors — driven by a
 * [ValueAnimator]. This keeps animation state local to each item (single-responsibility).
 */
internal class NavItemView(
    context: Context,
    val item: NavItem,
    private val config: Config
) : LinearLayout(context) {

    /** Styling forwarded from the parent nav view. */
    data class Config(
        val selectedColor: Int,
        val unselectedColor: Int,
        val iconSizePx: Float,
        /** Title text size already resolved to pixels. */
        val titleSizePx: Float,
        val animationDuration: Long,
        val badgeColor: Int,
        val badgeTextColor: Int
    )

    private val iconView = ImageView(context)
    private val titleView = TextView(context)

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = config.badgeColor }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = config.badgeTextColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val argb = ArgbEvaluator()
    private var animator: ValueAnimator? = null
    private var progress = 0f // 0 = unselected, 1 = selected

    var isItemSelected = false
        private set

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setWillNotDraw(false)
        alpha = if (item.enabled) 1f else 0.4f

        val iconLp = LayoutParams(config.iconSizePx.toInt(), config.iconSizePx.toInt())
        iconView.layoutParams = iconLp
        iconView.setImageResource(item.icon)
        iconView.setColorFilter(config.unselectedColor)
        addView(iconView)

        if (item.title.isNotEmpty()) {
            titleView.text = item.title
            titleView.setTextColor(config.unselectedColor)
            titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, config.titleSizePx)
            titleView.gravity = Gravity.CENTER
            titleView.setPadding(0, (2f).dpToPx().toInt(), 0, 0)
            val titleLp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            titleView.layoutParams = titleLp
            addView(titleView)
        }

        // Borderless ripple feedback on tap.
        foreground = rippleDrawable()
    }

    private fun rippleDrawable() = try {
        val outValue = android.util.TypedValue()
        context.theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless, outValue, true
        )
        ContextCompat.getDrawable(context, outValue.resourceId)
    } catch (t: Throwable) {
        null
    }

    fun setSelectedState(selected: Boolean, animate: Boolean) {
        if (selected == isItemSelected && progress == (if (selected) 1f else 0f)) return
        isItemSelected = selected
        val target = if (selected) 1f else 0f
        animator?.cancel()

        if (!animate) {
            applyProgress(target)
            return
        }

        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = config.animationDuration
            interpolator = OvershootInterpolator(2f)
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            start()
        }
    }

    private fun applyProgress(value: Float) {
        progress = value
        val scale = 1f + 0.18f * value
        iconView.scaleX = scale
        iconView.scaleY = scale
        iconView.translationY = -(4f.dpToPx()) * value

        val color = argb.evaluate(value, config.unselectedColor, config.selectedColor) as Int
        iconView.setColorFilter(color)
        if (item.title.isNotEmpty()) titleView.setTextColor(color)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBadge(canvas)
    }

    private fun drawBadge(canvas: Canvas) {
        val showNumber = item.badgeCount > 0
        val showDot = item.badgeDot && !showNumber
        if (!showNumber && !showDot) return

        val cx = iconView.right - 2f.dpToPx()
        val cy = iconView.top + 4f.dpToPx()

        if (showDot) {
            canvas.drawCircle(cx, cy, 4f.dpToPx(), badgePaint)
            return
        }

        val text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString()
        badgeTextPaint.textSize = 9f.dpToPx()
        val radius = max(8f.dpToPx(), badgeTextPaint.measureText(text) / 2f + 4f.dpToPx())
        canvas.drawCircle(cx, cy, radius, badgePaint)
        val textY = cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(text, cx, textY, badgeTextPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
