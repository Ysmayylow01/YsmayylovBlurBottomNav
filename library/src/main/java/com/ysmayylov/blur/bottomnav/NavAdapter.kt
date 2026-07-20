package com.ysmayylov.blur.bottomnav

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * Builds and manages the row of [NavItemView]s.
 *
 * The parent [YsmayylovBlurBottomNavView] delegates item creation, selection bookkeeping and click
 * wiring here so the parent can stay focused on the glass/blur rendering. This is a plain
 * horizontal [LinearLayout] with each item weighted equally.
 */
internal class NavAdapter(
    context: Context,
    private val config: NavItemView.Config,
    private val itemSpacingPx: Int,
    private val onItemClicked: (index: Int, item: NavItem) -> Unit
) : LinearLayout(context) {

    private val itemViews = mutableListOf<NavItemView>()
    var selectedIndex = -1
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun submit(items: List<NavItem>) {
        removeAllViews()
        itemViews.clear()
        selectedIndex = -1

        items.forEachIndexed { index, item ->
            val itemView = NavItemView(context, item, config)
            val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = if (index == 0) 0 else itemSpacingPx / 2
                marginEnd = if (index == items.lastIndex) 0 else itemSpacingPx / 2
            }
            itemView.layoutParams = lp
            if (item.enabled) {
                itemView.setOnClickListener { onItemClicked(index, item) }
            }
            addView(itemView)
            itemViews.add(itemView)
        }
    }

    fun select(index: Int, animate: Boolean) {
        if (index < 0 || index >= itemViews.size) return
        itemViews.forEachIndexed { i, view ->
            view.setSelectedState(i == index, animate)
        }
        selectedIndex = index
    }

    /** Center X of the given item within this container, used to position the floating indicator. */
    fun centerXOf(index: Int): Float {
        val view: View = itemViews.getOrNull(index) ?: return 0f
        return view.x + view.width / 2f
    }

    fun widthOf(index: Int): Int = itemViews.getOrNull(index)?.width ?: 0

    val count: Int get() = itemViews.size
}
