package com.ysmayylov.blur.bottomnav

import androidx.annotation.DrawableRes

/**
 * Immutable description of a single bottom-navigation destination.
 *
 * @property id          stable identifier reported by the selection listener.
 * @property icon        drawable resource shown for the item.
 * @property title       label rendered under the icon (may be empty to show icon only).
 * @property badgeCount  optional badge number; `0` or negative hides the badge.
 * @property badgeDot    when true, shows a small dot instead of a number (ignored if [badgeCount] > 0).
 * @property enabled     when false the item is shown dimmed and ignores clicks.
 */
data class NavItem @JvmOverloads constructor(
    val id: String,
    @DrawableRes val icon: Int,
    val title: CharSequence = "",
    val badgeCount: Int = 0,
    val badgeDot: Boolean = false,
    val enabled: Boolean = true
)
