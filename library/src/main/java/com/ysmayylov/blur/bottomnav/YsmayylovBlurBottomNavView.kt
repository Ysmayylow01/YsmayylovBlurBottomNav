package com.ysmayylov.blur.bottomnav

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.ysmayylov.blur.bottomnav.controller.BlurController
import com.ysmayylov.blur.bottomnav.controller.PreDrawBlurController
import com.ysmayylov.blur.bottomnav.util.dpToPx
import com.ysmayylov.blur.bottomnav.util.spToPx

/**
 * **Ysmayylov Blur Bottom Nav** — a floating glassmorphism bottom navigation for classic Android
 * XML layouts. It blurs whatever scrolls behind it in real time (GPU on Android 12+, a hand-written
 * Stack/Gaussian blur below that), tints it like frosted glass, rounds its corners, and hosts an
 * animated set of navigation items with a floating selected indicator.
 *
 * ```xml
 * <com.ysmayylov.blur.bottomnav.YsmayylovBlurBottomNavView
 *     android:layout_width="match_parent"
 *     android:layout_height="76dp"
 *     app:blurEnabled="true"
 *     app:blurRadius="25dp"
 *     app:cornerRadius="38dp"
 *     app:tintColor="#80FFFFFF" />
 * ```
 *
 * No third-party blur dependency is used — the entire blur engine lives in this library.
 */
class YsmayylovBlurBottomNavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ---- Glass / blur configuration -----------------------------------------
    private var blurEnabled = true
    private var blurRadiusPx = 25f.dpToPx()
    private var blurUpdateInterval = 0L
    private var cornerRadiusPx = 32f.dpToPx()
    private var tintColor = 0x66FFFFFF
    private var backgroundOverlayColor = Color.TRANSPARENT
    private var backgroundOverlayAlpha = 1f
    private var borderColor = 0x40FFFFFF
    private var borderWidthPx = 1f.dpToPx()
    private var shadowEnabled = true
    private var gradientOverlayEnabled = true

    // ---- Item configuration -------------------------------------------------
    private var selectedColor = 0xFFFFFFFF.toInt()
    private var unselectedColor = 0x99FFFFFF.toInt()
    private var iconSizePx = 24f.dpToPx()
    private var titleSizePx = 11f.spToPx()
    private var itemSpacingPx = 4f.dpToPx().toInt()
    private var animationDuration = 300L
    private var indicatorColor = 0x33FFFFFF
    private var badgeColor = 0xFFE53935.toInt()
    private var badgeTextColor = Color.WHITE

    // ---- Drawing helpers ----------------------------------------------------
    private val rect = RectF()
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- Collaborators ------------------------------------------------------
    private lateinit var controller: BlurController
    private lateinit var adapter: NavAdapter

    private var items: List<NavItem> = emptyList()
    private var selectedListener: ((id: String) -> Unit)? = null
    private var indexListener: ((index: Int) -> Unit)? = null

    // ---- Floating indicator animation --------------------------------------
    private var indicatorCenterX = 0f
    private var indicatorWidth = 0f
    private var indicatorAnimator: ValueAnimator? = null

    init {
        readAttributes(attrs, defStyleAttr)
        setWillNotDraw(false)
        clipChildren = false
        setupOutline()
        setupItemContainer()
    }

    // =========================================================================
    // Attribute parsing
    // =========================================================================
    private fun readAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.YsmayylovBlurBottomNavView, defStyleAttr, 0
        )
        try {
            blurEnabled = a.getBoolean(R.styleable.YsmayylovBlurBottomNavView_blurEnabled, blurEnabled)
            blurRadiusPx = a.getDimension(R.styleable.YsmayylovBlurBottomNavView_blurRadius, blurRadiusPx)
            blurUpdateInterval = a.getInt(
                R.styleable.YsmayylovBlurBottomNavView_blurUpdateInterval, blurUpdateInterval.toInt()
            ).toLong()
            cornerRadiusPx = a.getDimension(R.styleable.YsmayylovBlurBottomNavView_cornerRadius, cornerRadiusPx)
            backgroundOverlayColor = a.getColor(
                R.styleable.YsmayylovBlurBottomNavView_backgroundColor, backgroundOverlayColor
            )
            backgroundOverlayAlpha = a.getFloat(
                R.styleable.YsmayylovBlurBottomNavView_backgroundAlpha, backgroundOverlayAlpha
            )
            tintColor = a.getColor(R.styleable.YsmayylovBlurBottomNavView_tintColor, tintColor)
            borderColor = a.getColor(R.styleable.YsmayylovBlurBottomNavView_borderColor, borderColor)
            borderWidthPx = a.getDimension(R.styleable.YsmayylovBlurBottomNavView_borderWidth, borderWidthPx)
            shadowEnabled = a.getBoolean(R.styleable.YsmayylovBlurBottomNavView_shadowEnabled, shadowEnabled)
            gradientOverlayEnabled = a.getBoolean(
                R.styleable.YsmayylovBlurBottomNavView_gradientOverlayEnabled, gradientOverlayEnabled
            )
            selectedColor = a.getColor(R.styleable.YsmayylovBlurBottomNavView_selectedColor, selectedColor)
            unselectedColor = a.getColor(R.styleable.YsmayylovBlurBottomNavView_unSelectedColor, unselectedColor)
            indicatorColor = a.getColor(R.styleable.YsmayylovBlurBottomNavView_indicatorColor, indicatorColor)
            iconSizePx = a.getDimension(R.styleable.YsmayylovBlurBottomNavView_iconSize, iconSizePx)
            titleSizePx = a.getDimension(R.styleable.YsmayylovBlurBottomNavView_titleSize, titleSizePx)
            itemSpacingPx = a.getDimension(
                R.styleable.YsmayylovBlurBottomNavView_itemSpacing, itemSpacingPx.toFloat()
            ).toInt()
            animationDuration = a.getInt(
                R.styleable.YsmayylovBlurBottomNavView_animationDuration, animationDuration.toInt()
            ).toLong()
            badgeColor = a.getColor(R.styleable.YsmayylovBlurBottomNavView_badgeColor, badgeColor)
            badgeTextColor = a.getColor(R.styleable.YsmayylovBlurBottomNavView_badgeTextColor, badgeTextColor)
        } finally {
            a.recycle()
        }

        if (shadowEnabled && elevation == 0f) {
            elevation = 12f.dpToPx()
        }
    }

    private fun setupOutline() {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
            }
        }
        clipToOutline = true
    }

    private fun setupItemContainer() {
        adapter = NavAdapter(
            context = context,
            config = buildItemConfig(),
            itemSpacingPx = itemSpacingPx,
            onItemClicked = { index, item -> onItemClicked(index, item) }
        )
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }
        val hPad = 8f.dpToPx().toInt()
        adapter.setPadding(hPad, 0, hPad, 0)
        addView(adapter, lp)
    }

    private fun buildItemConfig() = NavItemView.Config(
        selectedColor = selectedColor,
        unselectedColor = unselectedColor,
        iconSizePx = iconSizePx,
        titleSizePx = titleSizePx,
        animationDuration = animationDuration,
        badgeColor = badgeColor,
        badgeTextColor = badgeTextColor
    )

    // =========================================================================
    // Lifecycle
    // =========================================================================
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val root = rootView ?: this
        controller = PreDrawBlurController(
            blurView = this,
            rootView = root,
            primaryEngine = PreDrawBlurController.defaultEngine()
        ).also {
            (it as PreDrawBlurController).cornerRadius = cornerRadiusPx
            it.setBlurEnabled(blurEnabled)
            it.setBlurRadius(blurRadiusPx)
            it.setUpdateInterval(blurUpdateInterval)
            it.attach()
        }
    }

    override fun onDetachedFromWindow() {
        indicatorAnimator?.cancel()
        if (::controller.isInitialized) controller.detach()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
        // Keep the indicator glued to the current selection after a size / rotation change.
        post {
            if (adapter.selectedIndex >= 0) moveIndicatorTo(adapter.selectedIndex, animate = false)
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Replaces the navigation items and selects the first enabled one. */
    fun setItems(items: List<NavItem>) {
        this.items = items
        adapter.submit(items)
        val first = items.indexOfFirst { it.enabled }
        if (first >= 0) {
            post {
                adapter.select(first, animate = false)
                moveIndicatorTo(first, animate = false)
            }
        }
    }

    /** Reports the selected item's [NavItem.id] whenever the selection changes. */
    fun setOnItemSelectedListener(listener: (id: String) -> Unit) {
        selectedListener = listener
    }

    /** Reports the selected item's index whenever the selection changes. */
    fun setOnItemSelectedIndexListener(listener: (index: Int) -> Unit) {
        indexListener = listener
    }

    /** Programmatically selects the item with the given [id]. */
    fun setSelectedItem(id: String, animate: Boolean = true) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) setSelectedIndex(index, animate)
    }

    /** Programmatically selects the item at [index]. */
    fun setSelectedIndex(index: Int, animate: Boolean = true) {
        if (index < 0 || index >= items.size) return
        adapter.select(index, animate)
        moveIndicatorTo(index, animate)
        notifySelection(index)
    }

    /** Updates the badge count for the item with the given [id]. Use 0 to hide it. */
    fun setBadgeCount(id: String, count: Int) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return
        items = items.toMutableList().also { it[idx] = it[idx].copy(badgeCount = count, badgeDot = false) }
        rebuildKeepingSelection()
    }

    /** Shows/hides a simple dot badge for the item with the given [id]. */
    fun setBadgeDot(id: String, show: Boolean) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx < 0) return
        items = items.toMutableList().also { it[idx] = it[idx].copy(badgeDot = show, badgeCount = 0) }
        rebuildKeepingSelection()
    }

    private fun rebuildKeepingSelection() {
        val selected = adapter.selectedIndex
        adapter.submit(items)
        if (selected >= 0) post {
            adapter.select(selected, animate = false)
            moveIndicatorTo(selected, animate = false)
        }
    }

    /** Enables or disables the live blur backdrop at runtime. */
    fun setBlurEnabled(enabled: Boolean) {
        blurEnabled = enabled
        if (::controller.isInitialized) controller.setBlurEnabled(enabled)
        invalidate()
    }

    /** Sets the blur radius in dp at runtime. */
    fun setBlurRadiusDp(dp: Float) {
        blurRadiusPx = dp.dpToPx()
        if (::controller.isInitialized) controller.setBlurRadius(blurRadiusPx)
    }

    /** Sets the corner radius in dp at runtime. */
    fun setCornerRadiusDp(dp: Float) {
        cornerRadiusPx = dp.dpToPx()
        (controller as? PreDrawBlurController)?.cornerRadius = cornerRadiusPx
        invalidateOutline()
        invalidate()
    }

    /** Sets the frosted-glass tint color at runtime. */
    fun setTintColor(color: Int) {
        tintColor = color
        invalidate()
    }

    /** Applies a full color scheme in one call (handy for theme switching). */
    fun applyColors(
        selected: Int = selectedColor,
        unselected: Int = unselectedColor,
        tint: Int = tintColor,
        border: Int = borderColor,
        indicator: Int = indicatorColor
    ) {
        selectedColor = selected
        unselectedColor = unselected
        tintColor = tint
        borderColor = border
        indicatorColor = indicator
        rebuildKeepingSelection()
        invalidate()
    }

    // =========================================================================
    // Selection plumbing
    // =========================================================================
    private fun onItemClicked(index: Int, item: NavItem) {
        adapter.select(index, animate = true)
        moveIndicatorTo(index, animate = true)
        notifySelection(index)
    }

    private fun notifySelection(index: Int) {
        val item = items.getOrNull(index) ?: return
        selectedListener?.invoke(item.id)
        indexListener?.invoke(index)
    }

    private fun moveIndicatorTo(index: Int, animate: Boolean) {
        if (width == 0) {
            post { moveIndicatorTo(index, animate) }
            return
        }
        val targetX = adapter.x + adapter.centerXOf(index)
        indicatorWidth = adapter.widthOf(index) * 0.62f
        indicatorAnimator?.cancel()

        if (!animate || indicatorCenterX == 0f) {
            indicatorCenterX = targetX
            invalidate()
            return
        }

        indicatorAnimator = ValueAnimator.ofFloat(indicatorCenterX, targetX).apply {
            duration = animationDuration
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener {
                indicatorCenterX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // =========================================================================
    // Rendering  (draw order: blur → tint → gradient → indicator → items → border)
    // =========================================================================
    override fun draw(canvas: Canvas) {
        // During the controller's internal capture pass this returns false so we don't draw
        // ourselves into our own blur buffer (which would recurse / show a feedback loop).
        if (::controller.isInitialized && !controller.draw(canvas)) return
        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())

        // Optional solid background overlay under the tint.
        if (backgroundOverlayColor != Color.TRANSPARENT) {
            overlayPaint.color = backgroundOverlayColor
            overlayPaint.alpha = (255 * backgroundOverlayAlpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, overlayPaint)
        }

        // Frosted-glass tint.
        tintPaint.color = tintColor
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, tintPaint)

        // Subtle top-down sheen so the glass reads as a physical surface.
        if (gradientOverlayEnabled) {
            gradientPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0x33FFFFFF, 0x0FFFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, gradientPaint)
            gradientPaint.shader = null
        }

        // Floating selected indicator (a soft pill behind the icons).
        if (indicatorCenterX > 0f && indicatorWidth > 0f) {
            val half = indicatorWidth / 2f
            val pad = 10f.dpToPx()
            val indicatorRect = RectF(
                indicatorCenterX - half, pad,
                indicatorCenterX + half, height - pad
            )
            indicatorPaint.color = indicatorColor
            val r = indicatorRect.height() / 2f
            canvas.drawRoundRect(indicatorRect, r, r, indicatorPaint)
        }
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (borderWidthPx <= 0f) return
        val inset = borderWidthPx / 2f
        rect.set(inset, inset, width - inset, height - inset)
        borderPaint.color = borderColor
        borderPaint.strokeWidth = borderWidthPx
        val r = cornerRadiusPx - inset
        canvas.drawRoundRect(rect, r, r, borderPaint)
    }
}
