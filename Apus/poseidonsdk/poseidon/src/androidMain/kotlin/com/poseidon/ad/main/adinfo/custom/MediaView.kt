package com.poseidon.ad.main.adinfo.custom

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.poseidon.ad.common.toolkit.dp2px
import com.poseidon.ad.common.toolkit.screenWidth
import kotlin.math.max

class MediaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes)

internal fun maxMediaViewHeight(context: Context): Int {
    val halfOfScreenWidth = screenWidth(context).toFloat() / 2.0F
    return max(dp2px(context = context, value = 120F), halfOfScreenWidth).toInt()
}