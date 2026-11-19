package com.poseidon.ad.mediation.admob.show

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.dp2px
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.adinfo.custom.AdViewBinding
import com.poseidon.ad.main.adinfo.custom.makeAdLabelView
import com.poseidon.ad.main.adinfo.custom.maxMediaViewHeight
import com.poseidon.ad.main.load.NativeParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformNativeAdShowParameter
import com.poseidon.ad.mediation.admob.contentToString
import com.poseidon.ad.mediation.admob.resolveAdPaidInfo
import com.poseidon.ad.mediation.admob.resolveAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * AdMob 原生包装类，负责渲染、事件派发与生命周期。
 */
internal class AdmobNativeAd(
    @AdSourceIdDef private val adSourceId: Int,
    private var thirdPartyAd: NativeAd?,
    private val parameter: NativeParameter
) : NativeAdBase() {

    private val adListenerAtomic: AtomicReference<AdShowListener> = AtomicReference()

    init {
        updateEstimatedEcpmAdSourceInfo(thirdPartyAd?.responseInfo.resolveAdSourceInfo())
    }

    override fun getAdSourceId(): Int = adSourceId

    override fun setAdListener(listener: AdShowListener) {
        adListenerAtomic.set(listener)
    }

    fun dispatchAdClickedEvent() {
        logInfo(TAG) { "Ad was clicked." }
        adListenerAtomic.get()?.onAdClicked()
        val adSourceInfo = thirdPartyAd?.responseInfo.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        super.dispatchAdClickedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
    }

    fun dispatchAdImpressionEvent() {
        logInfo(TAG) { "Ad recorded an impression." }
        adListenerAtomic.get()?.onAdShowSuccess()
        val adSourceInfo = thirdPartyAd?.responseInfo.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        super.dispatchAdImpressionEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
    }

    fun dispatchAdClosedEvent() {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        logInfo(TAG) { "Ad closed." }
        adListenerAtomic.get()?.onAdClosed()
        val adSourceInfo = thirdPartyAd?.responseInfo.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        super.dispatchAdClosedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
        runCatching {
            destroy()
        }
    }

    override fun onAdForceClosed() {
        dispatchAdClosedEvent()
        runCatching {
            destroy()
        }
    }

    override fun render(showParameter: PlatformNativeAdShowParameter): ShowResultCallback {
        val thirdPartyAd = thirdPartyAd
            ?: return ShowResultCallback.Failure(
                code = ERROR_CODE_NULL_THIRD_PARTY_AD,
                "AdmobNativeAd is null."
            )

        thirdPartyAd.setOnPaidEventListener { adValue ->
            updateEstimatedEcpmAdSourceInfo(thirdPartyAd.responseInfo.resolveAdSourceInfo())
            val adPaidInfo = resolveAdPaidInfo(
                responseInfo = thirdPartyAd.responseInfo,
                adValue = adValue,
                loadParameter = parameter,
            )
            logInfo(TAG) {
                "onPaidEvent: adValue=${adValue.contentToString()}, adPaidInfo=$adPaidInfo"
            }
            dispatchOnPaidEvent(adPaidInfo)
        }

        val templateView = showParameter.templateView
        val context = templateView.context
        if (context is AppCompatActivity) {
            context.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    destroy()
                }
            })
        }

        val admobNativeAdView = NativeAdView(context)
        admobNativeAdView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                logInfo(TAG) { "AdmobNativeAd.render.onViewAttachedToWindow: $v" }
            }

            override fun onViewDetachedFromWindow(v: View) {
                logInfo(TAG) { "AdmobNativeAd.render.onViewDetachedFromWindow: $v" }
                admobNativeAdView.removeOnAttachStateChangeListener(this)
                dispatchAdClosedEvent()
            }
        })

        templateView.removeAllViews()
        templateView.addView(admobNativeAdView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val binding = templateView.inflateAdContentViewTo(admobNativeAdView)

        val mediaContent = thirdPartyAd.mediaContent
        logInfo(TAG) {
            buildString {
                append("Render AdMob native: scene=${parameter.sceneName}, ")
                append("headline=${thirdPartyAd.headline}, body=${thirdPartyAd.body}, ")
                append("iconUri=${thirdPartyAd.icon?.uri}, hasIconDrawable=${thirdPartyAd.icon?.drawable != null}, ")
                append("callToAction=${thirdPartyAd.callToAction}, starRating=${thirdPartyAd.starRating}, ")
                append("mediaContent=$mediaContent, hasVideo=${mediaContent?.hasVideoContent()}, ")
                append("images=${thirdPartyAd.images?.mapNotNull { it.uri }}, price=${thirdPartyAd.price}, store=${thirdPartyAd.store}")
            }
        }

        binding.bindSelfRenderView(context = context, admobNativeAdView = admobNativeAdView, thirdPartyAd = thirdPartyAd)

        templateView.addView(
            makeAdLabelView(context),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp2px(context, 5F).toInt()
                topMargin = dp2px(context, 5F).toInt()
                marginEnd = dp2px(context, 5F).toInt()
                bottomMargin = dp2px(context, 5F).toInt()
            })
        return ShowResultCallback.Success
    }

    private fun AdViewBinding.bindSelfRenderView(
        context: Context,
        admobNativeAdView: NativeAdView,
        thirdPartyAd: NativeAd
    ) {
        val title: String? = thirdPartyAd.headline
        if (title.isNullOrEmpty()) {
            titleView.isVisible = false
        } else {
            titleView.text = title
            admobNativeAdView.headlineView = titleView
            titleView.isVisible = true
        }

        val descriptionText: String? = thirdPartyAd.body
        if (descriptionText.isNullOrEmpty()) {
            descriptionView.isVisible = false
        } else {
            descriptionView.text = descriptionText
            admobNativeAdView.bodyView = descriptionView
            descriptionView.isVisible = true
        }

        val thirdPartyAdIcon = thirdPartyAd.icon
        val thirdPartyAdIconDrawable: Drawable? = thirdPartyAdIcon?.drawable
        adIconView.removeAllViews()
        if (thirdPartyAdIconDrawable != null) {
            val iconView = ImageView(context)
            iconView.setImageDrawable(thirdPartyAdIconDrawable)
            adIconView.addView(iconView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            admobNativeAdView.iconView = iconView
            adIconView.isVisible = true
        } else {
            adIconView.visibility = View.GONE
        }

        val mediaContent = thirdPartyAd.mediaContent
        if (mediaContent != null) {
            val admobMediaView = MediaView(context)
            admobMediaView.mediaContent = mediaContent
            mediaView.addView(
                admobMediaView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    maxMediaViewHeight(context)
                )
            )
            admobNativeAdView.mediaView = admobMediaView
            mediaView.visibility = View.VISIBLE
        } else {
            mediaView.removeAllViews()
            mediaView.visibility = View.GONE
        }

        adLogoView.removeAllViews()
        val adChoicesIcon = thirdPartyAd.adChoicesInfo?.images?.firstOrNull()?.drawable
        if (adChoicesIcon != null) {
            val adChoicesIconView = ImageView(context)
            adChoicesIconView.setImageDrawable(adChoicesIcon)
            val admobAdChoicesView = AdChoicesView(context)
            admobAdChoicesView.addView(adChoicesIconView)
            adLogoView.addView(
                admobAdChoicesView,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40F, context.resources.displayMetrics).toInt(),
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20F, context.resources.displayMetrics).toInt(),
            )
            admobNativeAdView.adChoicesView = admobAdChoicesView
            adLogoView.visibility = View.VISIBLE
        } else {
            adLogoView.visibility = View.GONE
        }

        val callToActionText = thirdPartyAd.callToAction
        if (callToActionText.isNullOrEmpty()) {
            callToActionView.isVisible = false
        } else {
            callToActionView.text = callToActionText
            admobNativeAdView.callToActionView = callToActionView
            callToActionView.isVisible = true
        }

        admobNativeAdView.setNativeAd(thirdPartyAd)
        admobNativeAdView.adChoicesView?.bringToFront()
    }

    override fun isReady(): Boolean = thirdPartyAd != null

    override fun getParameter(): Parameter = parameter

    override fun destroy() {
        adListenerAtomic.set(null)
        thirdPartyAd?.apply {
            destroy()
            thirdPartyAd = null
        }
    }

    companion object {
        private const val TAG = "AdmobNativeAd"
    }
}
