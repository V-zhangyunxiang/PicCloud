package com.poseidon.ad.main.adinfo.custom

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.withStyledAttributes
import com.poseidon.ad.BuildConfig
import com.poseidon.ad.R
import com.poseidon.ad.common.toolkit.dp2px
import java.util.Objects

class NativeTemplateView : FrameLayout {

    private var templateType: Int? = null
    private var ctaViewId: Int? = null
    private var mediaViewId: Int? = null
    private var adIconViewId: Int? = null
    private var titleViewId: Int? = null
    private var descriptionViewId: Int? = null
    private var adLogViewId: Int? = null

    constructor(context: Context) : this(context, null, 0, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
    ) : this(context, attrs, 0, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {

        context.withStyledAttributes(
            set = attrs,
            attrs = R.styleable.NativeTemplateView
        ) {
            templateType = getResourceId(
                R.styleable.NativeTemplateView_template_type,
                R.layout.medium_template_view
            )
            ctaViewId = getResourceId(
                R.styleable.NativeTemplateView_cta_view_id,
                R.id.cta
            )
            mediaViewId = getResourceId(
                R.styleable.NativeTemplateView_media_view_id,
                R.id.media
            )
            adIconViewId = getResourceId(
                R.styleable.NativeTemplateView_ad_icon_view_id,
                R.id.ad_icon
            )
            titleViewId = getResourceId(
                R.styleable.NativeTemplateView_title_view_id,
                R.id.title
            )
            descriptionViewId = getResourceId(
                R.styleable.NativeTemplateView_description_view_id,
                R.id.description
            )
            adLogViewId = getResourceId(
                R.styleable.NativeTemplateView_ad_log_view_id,
                R.id.ad_log
            )
        }
    }

    internal fun inflateAdContentViewTo(thirdPartyAdContainer: ViewGroup): AdViewBinding {
        val templateType = templateType ?: error("templateType is null.")

        thirdPartyAdContainer.removeAllViews()
        LayoutInflater.from(context).inflate(templateType, thirdPartyAdContainer)

        val cta = thirdPartyAdContainer.findView<Button>(ctaViewId).getOrElse { t ->
            if (BuildConfig.POSEIDON_DEBUG_SWITCH) error("Failed to make CTA view by id($ctaViewId) because `${t.message}`.") else null
        }
        val mediaView = thirdPartyAdContainer.findView<MediaView>(mediaViewId).getOrElse { t ->
            if (BuildConfig.POSEIDON_DEBUG_SWITCH) error("Failed to make MediaView view by id($mediaViewId) because `${t.message}`.") else null
        }
        val adIconView = thirdPartyAdContainer.findView<AdIconView>(adIconViewId).getOrElse { t ->
            if (BuildConfig.POSEIDON_DEBUG_SWITCH) error("Failed to make AdIconView view by id($adIconViewId) because `${t.message}`.") else null
        }
        val titleView = thirdPartyAdContainer.findView<TextView>(titleViewId).getOrElse { t ->
            if (BuildConfig.POSEIDON_DEBUG_SWITCH) error("Failed to make TitleView view by id($titleViewId) because `${t.message}`.") else null
        }
        val descriptionView =
            thirdPartyAdContainer.findView<TextView>(descriptionViewId).getOrElse { t ->
                if (BuildConfig.POSEIDON_DEBUG_SWITCH) error("Failed to make DescriptionView view by id($descriptionViewId) because `${t.message}`.") else null
            }
        val adLogView = thirdPartyAdContainer.findView<AdLogoView>(adLogViewId).getOrElse { t ->
            if (BuildConfig.POSEIDON_DEBUG_SWITCH) error("Failed to make AdLogoView view by id($adLogViewId) because `${t.message}`.") else null
        }

        val firstChild = thirdPartyAdContainer.getChildAt(0)
        require(firstChild is NativeAdView) { "Root Container of layout setting by `app:template_type` must be instance of ${NativeAdView::class.java}" }
        return AdViewBinding {
            setRootView(firstChild)
            setCallToActionView(cta)
            setMediaView(mediaView)
            setAdIconView(adIconView)
            setTitleView(titleView)
            setDescriptionView(descriptionView)
            setAdLogoView(adLogView)
        }
    }

    override fun isAttachedToWindow(): Boolean {
        return super.isAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    companion object {
        private inline fun <reified V : View> View.findView(viewId: Int?): Result<V> {
            return runCatching { findViewById<View>(requireNotNull(viewId) { "viewId is null" }) as V }
        }
    }
}

internal class AdViewBinding private constructor(
    val rootView: NativeAdView,
    val titleView: TextView,
    val descriptionView: TextView,
    val adIconView: AdIconView,
    val callToActionView: Button,
    val adLogoView: AdLogoView,
    val mediaView: MediaView,
) {
    override fun toString() =
        "AdViewBinding(rootView=$rootView, titleView=$titleView, descriptionView=$descriptionView, adIconView=$adIconView, callToActionView=$callToActionView, adLogoView=$adLogoView, mediaView=$mediaView)"

    override fun equals(other: Any?) = other is AdViewBinding
            && rootView == other.rootView
            && titleView == other.titleView
            && descriptionView == other.descriptionView
            && adIconView == other.adIconView
            && callToActionView == other.callToActionView
            && adLogoView == other.adLogoView
            && mediaView == other.mediaView

    override fun hashCode() = Objects.hash(
        rootView,
        titleView,
        descriptionView,
        adIconView,
        callToActionView,
        adLogoView,
        mediaView
    )

    class Builder {
        @set:JvmSynthetic // Hide 'void' setter from Java
        var rootView: NativeAdView? = null

        @set:JvmSynthetic // Hide 'void' setter from Java
        var titleView: TextView? = null

        @set:JvmSynthetic // Hide 'void' setter from Java
        var descriptionView: TextView? = null

        @set:JvmSynthetic // Hide 'void' setter from Java
        var adIconView: AdIconView? = null

        @set:JvmSynthetic // Hide 'void' setter from Java
        var callToActionView: Button? = null

        @set:JvmSynthetic // Hide 'void' setter from Java
        var adLogoView: AdLogoView? = null

        @set:JvmSynthetic // Hide 'void' setter from Java
        var mediaView: MediaView? = null

        fun setRootView(rootView: NativeAdView?) = apply { this.rootView = rootView }

        fun setTitleView(titleView: TextView?) = apply { this.titleView = titleView }

        fun setDescriptionView(descriptionView: TextView?) =
            apply { this.descriptionView = descriptionView }

        fun setAdIconView(adIconView: AdIconView?) = apply { this.adIconView = adIconView }

        fun setCallToActionView(callToActionView: Button?) =
            apply { this.callToActionView = callToActionView }

        fun setAdLogoView(adLogoView: AdLogoView?) = apply { this.adLogoView = adLogoView }

        fun setMediaView(mediaView: MediaView?) = apply { this.mediaView = mediaView }

        fun build() = AdViewBinding(
            rootView = rootView!!,
            titleView = titleView!!,
            descriptionView = descriptionView!!,
            adIconView = adIconView!!,
            callToActionView = callToActionView!!,
            adLogoView = adLogoView!!,
            mediaView = mediaView!!,
        )
    }
}

@JvmSynthetic // Hide from Java callers who should use Builder.
internal fun AdViewBinding(initializer: AdViewBinding.Builder.() -> Unit): AdViewBinding {
    return AdViewBinding.Builder().apply(initializer).build()
}

fun makeAdLabelView(context: Context): View {
    return TextView(context).also { textView: TextView ->
        textView.setPadding(dp2px(context, 5F).toInt(), 0, dp2px(context, 5F).toInt(), 0)
        textView.setTextColor(context.getColor(R.color.color_ffffffff))
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12F)
        textView.setText(R.string.ad_label)
        textView.background =
            AppCompatResources.getDrawable(context, R.drawable.shape_secondary_radius2)
    }
}
