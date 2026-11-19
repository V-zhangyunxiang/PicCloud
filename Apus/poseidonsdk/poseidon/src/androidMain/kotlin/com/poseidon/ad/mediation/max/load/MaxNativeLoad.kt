package com.poseidon.ad.mediation.max.load

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.dp2px
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.adinfo.custom.makeAdLabelView
import com.poseidon.ad.main.adinfo.custom.maxMediaViewHeight
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.NativeAdLoader
import com.poseidon.ad.main.load.NativeParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformNativeAdLoadParameter
import com.poseidon.ad.main.processor.PlatformNativeAdShowParameter
import com.poseidon.ad.mediation.max.contentToString
import com.poseidon.ad.mediation.max.init.MaxSetup
import com.poseidon.ad.mediation.max.resolveAdPaidInfo
import com.poseidon.ad.mediation.max.resolveAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

internal class MaxNativeLoad(parameter: NativeParameter) : NativeAdLoader(parameter = parameter) {

    override fun mediationAdLoad(
        loadParameter: PlatformNativeAdLoadParameter,
        loadCallback: InnerAdLoadCallback<NativeAdBase>
    ) {
        val thirdPartyAd = MaxNativeAdLoader(parameter.adUnitId)
        thirdPartyAd.setNativeAdListener(object : MaxNativeAdListener() {

            private val maxNativeAdAtomic = AtomicReference<MaxNativeAd>(null)

            override fun onNativeAdLoaded(nativeAdView: MaxNativeAdView?, maxAd: MaxAd) {
                markAdRequestReturned()
                val adSourceInfo = maxAd.resolveAdSourceInfo()
                logInfo(TAG) {
                    "onNativeAdLoaded: maxAd=${maxAd.contentToString()}, nativeAdView=${nativeAdView}, adSourceInfo=${adSourceInfo}"
                }
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                val maxNativeAd = MaxNativeAd(
                    adSourceId = adSourceId,
                    thirdPartyAd = thirdPartyAd,
                    maxAd = maxAd,
                    parameter = parameter as NativeParameter
                )
                dispatchAdLoadedEvent(
                    adSourceId = adSourceId,
                    adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                    revenue = resolveAdPaidInfo(maxAd = maxAd, loadParameter = parameter).revenue,
                    poseidonConfigVersion = parameter.poseidonConfigVersion,
                    adSourceName = adSourceInfo?.adSourceName,
                    estimatedRevenue = maxNativeAd.estimatedEcpm(adSourceInfo) ?: -1.0
                )
                maxNativeAdAtomic.set(maxNativeAd)
                loadCallback.onAdLoadSuccess(maxNativeAd)
            }

            override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                markAdRequestReturned()
                logInfo(TAG) {
                    "onNativeAdLoadFailed: adUnitId=$adUnitId, error=${error.contentToString()}"
                }
                val code = error.code
                val message = error.message
                dispatchAdFailedLoadedEvent(
                    code = code,
                    message = message,
                    poseidonConfigVersion = parameter.poseidonConfigVersion
                )
                loadCallback.onAdLoadFailed(code = code, message = message)
            }

            override fun onNativeAdClicked(maxAd: MaxAd) {
                maxNativeAdAtomic.get()?.dispatchAdClickedEvent(maxAd = maxAd)
            }
        })
        markAdRequestStarted()
        thirdPartyAd.loadAd()
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = MaxSetup

    companion object {
        private const val TAG = "MaxNativeAdLoader"
    }

    private class MaxNativeAd(
        @AdSourceIdDef private val adSourceId: Int,
        private var thirdPartyAd: MaxNativeAdLoader?,
        private var maxAd: MaxAd?,
        private var parameter: NativeParameter,
    ) : NativeAdBase() {

        override fun getAdSourceId(): Int = adSourceId

        private val adListenerAtomic: AtomicReference<AdShowListener> = AtomicReference()

        init {
            updateEstimatedEcpmAdSourceInfo(maxAd.resolveAdSourceInfo())
        }

        override fun setAdListener(listener: AdShowListener) {
            adListenerAtomic.set(listener)
        }

        fun dispatchAdClickedEvent(maxAd: MaxAd) {
            val adSourceInfo = maxAd.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            logInfo(TAG) {
                "Ad was clicked. maxAd=${maxAd.contentToString()}, adSourceInfo=${adSourceInfo}"
            }
            adListenerAtomic.get()?.onAdClicked()
            super.dispatchAdClickedEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
        }

        fun dispatchAdImpressionEvent() {
            val adSourceInfo = maxAd.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            logInfo(TAG) {
                "Ad recorded an impression. maxAd=${maxAd?.contentToString()}, adSourceInfo=${adSourceInfo}"
            }
            adListenerAtomic.get()?.onAdShowSuccess()
            super.dispatchAdImpressionEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
        }

        override fun onAdForceClosed() {
            dispatchAdClosedEvent()
        }

        fun dispatchAdClosedEvent() {
            if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
                return
            }
            val adSourceInfo = maxAd.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            logInfo(TAG) {
                "Ad closed. maxAd=${maxAd?.contentToString()}, adSourceInfo=${adSourceInfo}"
            }
            adListenerAtomic.get()?.onAdClosed()
            super.dispatchAdClosedEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
            runCatching {
                destroy()
            }
        }

        override fun render(showParameter: PlatformNativeAdShowParameter): ShowResultCallback {
            val thirdPartyAd = thirdPartyAd
                ?: return ShowResultCallback.Failure(
                    code = ERROR_CODE_NULL_THIRD_PARTY_AD,
                    "MaxNativeAdLoader is null."
                )
            val maxAd = maxAd
                ?: return ShowResultCallback.Failure(
                    code = ERROR_CODE_NULL_THIRD_PARTY_AD,
                    "MaxAd is null."
                )

            thirdPartyAd.setRevenueListener { revenueMaxAd ->
                val adSourceInfo = revenueMaxAd.resolveAdSourceInfo()
                updateEstimatedEcpmAdSourceInfo(adSourceInfo)
                val adPaidInfo = resolveAdPaidInfo(
                    maxAd = revenueMaxAd,
                    loadParameter = parameter,
                    adSourceInfo = adSourceInfo
                )
                logInfo(TAG) {
                    "setRevenueListener: revenueMaxAd=${revenueMaxAd.contentToString()}, adPaidInfo=$adPaidInfo"
                }
                dispatchOnPaidEvent(adPaidInfo)
                dispatchAdImpressionEvent()
            }

            val templateView = showParameter.templateView

            val context = templateView.context

            val mainView = FrameLayout(context)
            val binding = templateView.inflateAdContentViewTo(mainView)

            binding.mediaView.updateLayoutParams<ViewGroup.LayoutParams> {
                height = maxMediaViewHeight(context)
            }

            val nativeAd = maxAd.nativeAd
            logInfo(TAG) {
                buildString {
                    append("Render MAX native: scene=${parameter.sceneName}, ")
                    append("title=${nativeAd?.title}, body=${nativeAd?.body}, ")
                    append("iconUri=${nativeAd?.icon?.uri}, hasIconDrawable=${nativeAd?.icon?.drawable != null}, ")
                    append("callToAction=${nativeAd?.callToAction}, advertiser=${nativeAd?.advertiser}, ")
                    append("mediaView=${nativeAd?.mediaView}, mainImage=${nativeAd?.mainImage}")
                }
            }

            val iconImageView = ImageView(context)
            iconImageView.id = View.generateViewId()
            binding.adIconView.addView(
                iconImageView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val binder: MaxNativeAdViewBinder =
                MaxNativeAdViewBinder.Builder(mainView)
                    .setTitleTextViewId(binding.titleView.id)
                    .setBodyTextViewId(binding.descriptionView.id)
                    .setIconImageViewId(iconImageView.id)
                    .setMediaContentViewGroupId(binding.mediaView.id)
                    .setCallToActionButtonId(binding.callToActionView.id)
                    .build()

            val maxNativeAdView = MaxNativeAdView(binder, context)
            thirdPartyAd.render(maxNativeAdView, maxAd)

            logInfo(TAG) {
                "render: clickableViews=${maxNativeAdView.clickableViews.joinToString(",")}"
            }

            templateView.removeAllViews()
            maxNativeAdView.addOnAttachStateChangeListener(object :
                View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    logInfo(TAG) {
                        "MaxNativeAd.render.onViewAttachedToWindow: $v"
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    logInfo(TAG) {
                        "MaxNativeAd.render.onViewDetachedFromWindow: $v"
                    }
                    maxNativeAdView.removeOnAttachStateChangeListener(this)
                    dispatchAdClosedEvent()
                }
            })
            templateView.addView(maxNativeAdView)

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

        override fun isReady(): Boolean {
            return thirdPartyAd != null && maxAd != null
        }

        override fun getParameter(): Parameter = parameter

        override fun eCPM(): Double? = maxAd?.let { ad ->
            val adSourceInfo = ad.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            resolveAdPaidInfo(
                maxAd = ad,
                loadParameter = parameter,
                adSourceInfo = adSourceInfo
            ).eCPM.takeIf { it > 0.0 } ?: estimatedEcpm(adSourceInfo)
        } ?: super.eCPM()

        override fun destroy() {
            adListenerAtomic.set(null)
            thirdPartyAd?.apply {
                setNativeAdListener(null)
                maxAd?.also { maxAd ->
                    destroy(maxAd)
                }
                destroy()
                thirdPartyAd = null
            }
            maxAd = null
        }

        companion object {
            private const val TAG = "MaxNativeAd"
        }
    }
}
