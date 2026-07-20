package com.ysmayylov.blur.bottomnav.util

import android.content.res.Resources
import android.util.TypedValue

/** Converts a dp value to pixels using the device density. */
internal fun Float.dpToPx(): Float =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this, Resources.getSystem().displayMetrics
    )

/** Converts an sp value to pixels using the device density + font scale. */
internal fun Float.spToPx(): Float =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, this, Resources.getSystem().displayMetrics
    )

internal fun Int.dpToPx(): Float = this.toFloat().dpToPx()
