package com.poseidon.ad.mediation.topon

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import com.thinkup.nativead.api.TUNativeImageView
import com.poseidon.ad.common.toolkit.dp2px

class MultiImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    val padding: Int

    init {
        orientation = HORIZONTAL
        padding = (dp2px(context, 5f) + 0.5).toInt()
    }

    fun setImageList(imageList: List<String>, imageWidth: Int, imageHeight: Int) {
        removeAllViews()
        val size = imageList.size
        for (url in imageList) {
            val width = resources.displayMetrics.widthPixels

            val atNativeImageView = TUNativeImageView(context)
            atNativeImageView.setImage(url)
            atNativeImageView.setPadding(padding, padding, padding, padding)
            val layoutParams =
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, width * 600 / size / 1024)
            if (imageWidth > 0 && imageHeight > 0) {
                layoutParams.height = width * imageHeight / size / imageWidth
            }
            layoutParams.weight = 1f

            addView(atNativeImageView, layoutParams)
        }
    }
}