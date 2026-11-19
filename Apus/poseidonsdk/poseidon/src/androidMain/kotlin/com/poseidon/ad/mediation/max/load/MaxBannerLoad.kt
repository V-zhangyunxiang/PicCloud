package com.poseidon.ad.mediation.max.load

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.poseidon.ad.R
import com.poseidon.ad.common.toolkit.DeviceHelper
import com.poseidon.ad.common.toolkit.DeviceHelper.DeviceType
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.BannerAdBase
import com.poseidon.ad.main.load.BannerAdLoad
import com.poseidon.ad.main.load.BannerParameter
import com.poseidon.ad.main.load.InnerAdLoadAndShowCallback
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.processor.PlatformBannerAdLoadAndShowParameter
import com.poseidon.ad.mediation.max.contentToString
import com.poseidon.ad.mediation.max.init.MaxSetup
import com.poseidon.ad.mediation.max.resolveAdPaidInfo
import com.poseidon.ad.mediation.max.resolveAdSourceInfo
import com.poseidon.ad.mediation.max.show.MaxBannerAd
import java.util.concurrent.atomic.AtomicReference

internal class MaxBannerLoad(parameter: BannerParameter) :
    BannerAdLoad(parameter = parameter) {

    /** 缓存当前 MaxAdView，便于销毁或复用 */
    private val adViewAtomic: AtomicReference<MaxAdView> = AtomicReference()

    override fun mediationAdLoad(
        loadParameter: PlatformBannerAdLoadAndShowParameter,
        loadCallback: InnerAdLoadAndShowCallback<BannerAdBase>
    ) {
        val context = loadParameter.context

        val adView = MaxAdView(parameter.adUnitId)
        adViewAtomic.set(adView)
        // Stretch to the width of the screen for banners to be fully functional
        val width = ViewGroup.LayoutParams.MATCH_PARENT

        // Banner height on phones and tablets is 50 and 90, respectively
        val heightPx = context.resources.getDimensionPixelSize(
            when (DeviceHelper.getDeviceType(context = context)) {
                DeviceType.Tablet -> R.dimen.max_banner_height_on_tablets
                DeviceType.Phone -> R.dimen.max_banner_height_on_phones
            }
        )

        adView.layoutParams = FrameLayout.LayoutParams(width, heightPx)

        // Set background color for banners to be fully functional
        adView.setBackgroundColor(Color.WHITE)

        val maxBannerAd = MaxBannerAd(
            parameter = parameter as BannerParameter,
            listener = loadCallback
        )

        // Replace ad container with new ad view.
        loadParameter.adViewContainer.removeAllViews()
        adView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                logInfo(TAG) { "onViewAttachedToWindow: $v" }
            }

            override fun onViewDetachedFromWindow(v: View) {
                logInfo(TAG) { "onViewDetachedFromWindow: $v" }
            }
        })
        loadParameter.adViewContainer.addView(adView)

        adView.setRevenueListener { revenueMaxAd ->
            val adPaidInfo = resolveAdPaidInfo(
                maxAd = revenueMaxAd,
                loadParameter = parameter,
            )
            logInfo(TAG) {
                "setRevenueListener: revenueMaxAd=${revenueMaxAd.contentToString()}, adPaidInfo=$adPaidInfo"
            }
            maxBannerAd.publicDispatchOnPaidEvent(adPaidInfo)
            maxBannerAd.dispatchAdImpressionEvent(maxAd = revenueMaxAd)
        }
        adView.setListener(object : MaxAdViewAdListener {
            override fun onAdLoaded(maxAd: MaxAd) {
                markAdRequestReturned()
                val adSourceInfo = maxAd.resolveAdSourceInfo()
                logInfo(TAG) {
                    "onAdLoaded: maxAd=${maxAd.contentToString()}, maxAdView=${adView}, adSourceInfo=${adSourceInfo}"
                }
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                dispatchAdLoadedEvent(
                    adSourceId = adSourceId,
                    adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                    revenue = resolveAdPaidInfo(maxAd = maxAd, loadParameter = parameter).revenue,
                    poseidonConfigVersion = parameter.poseidonConfigVersion,
                    adSourceName = adSourceInfo?.adSourceName,
                    estimatedRevenue = -1.0
                )
                maxBannerAd._adSourceId = adSourceId
                loadCallback.onAdLoadSuccess(maxBannerAd)
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                markAdRequestReturned()
                logInfo(TAG) {
                    "onAdLoadFailed: adUnitId=$adUnitId, error=${error.contentToString()}"
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

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                maxBannerAd.dispatchAdFailedToShowEvent(maxAd = ad, error = error)
            }

            override fun onAdClicked(maxAd: MaxAd) {
                maxBannerAd.dispatchAdClickedEvent(maxAd = maxAd)
            }

            override fun onAdExpanded(maxAd: MaxAd) {}

            override fun onAdCollapsed(maxAd: MaxAd) {}

            override fun onAdDisplayed(maxAd: MaxAd) { /* DO NOT USE - THIS IS RESERVED FOR FULLSCREEN ADS ONLY AND WILL BE REMOVED IN A FUTURE SDK RELEASE */
            }

            override fun onAdHidden(maxAd: MaxAd) { /* DO NOT USE - THIS IS RESERVED FOR FULLSCREEN ADS ONLY AND WILL BE REMOVED IN A FUTURE SDK RELEASE */
            }
        })
        markAdRequestStarted()
        adView.loadAd()
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = MaxSetup

    override fun destroy() {
        logInfo(TAG) { "destroy" }
        adViewAtomic.getAndSet(null)?.destroy()
    }

    companion object {
        private const val TAG = "MaxBannerAdLoader"
    }
}
