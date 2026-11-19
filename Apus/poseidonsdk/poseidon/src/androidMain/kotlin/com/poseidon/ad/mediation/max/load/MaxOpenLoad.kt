package com.poseidon.ad.mediation.max.load

import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.OpenAdBase
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.OpenAdLoader
import com.poseidon.ad.main.load.OpenParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformOpenAdLoadParameter
import com.poseidon.ad.main.processor.PlatformOpenAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.mediation.max.contentToString
import com.poseidon.ad.mediation.max.init.MaxSetup
import com.poseidon.ad.mediation.max.resolveAdPaidInfo
import com.poseidon.ad.mediation.max.resolveAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

internal class MaxOpenLoad(parameter: OpenParameter) :
    OpenAdLoader(parameter = parameter) {

    override fun mediationAdLoad(
        loadParameter: PlatformOpenAdLoadParameter,
        loadCallback: InnerAdLoadCallback<OpenAdBase>
    ) {
        val thirdPartyAd = MaxAppOpenAd(parameter.adUnitId)
        val listener = object : MaxAdListener {

            val maxOpenAdAtomic: AtomicReference<MaxOpenAd> = AtomicReference()

            override fun onAdLoaded(maxAd: MaxAd) {
                markAdRequestReturned()
                val adSourceInfo = maxAd.resolveAdSourceInfo()
                logInfo(TAG) {
                    "onAdLoaded: maxAd=${maxAd.contentToString()}}, adSourceInfo=$adSourceInfo"
                }
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                val maxOpenAd = MaxOpenAd(
                    adSourceId = adSourceId,
                    thirdPartyAd = thirdPartyAd,
                    maxAd = maxAd,
                    parameter = parameter as OpenParameter
                )
                dispatchAdLoadedEvent(
                    adSourceId = adSourceId,
                    adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                    revenue = resolveAdPaidInfo(maxAd = maxAd, loadParameter = parameter).revenue,
                    poseidonConfigVersion = parameter.poseidonConfigVersion,
                    adSourceName = adSourceInfo?.adSourceName,
                    estimatedRevenue = maxOpenAd.estimatedEcpm(adSourceInfo) ?: -1.0
                )
                maxOpenAdAtomic.set(maxOpenAd)
                loadCallback.onAdLoadSuccess(maxOpenAd)
            }

            override fun onAdDisplayed(maxAd: MaxAd) {
                maxOpenAdAtomic.get()?.dispatchAdImpressionEvent(maxAd = maxAd)
            }

            override fun onAdHidden(maxAd: MaxAd) {
                maxOpenAdAtomic.get()?.dispatchAdClosedEvent(maxAd = maxAd)
            }

            override fun onAdClicked(maxAd: MaxAd) {
                maxOpenAdAtomic.get()?.dispatchAdClickedEvent(maxAd = maxAd)
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                markAdRequestReturned()
                thirdPartyAd.setListener(null)
                thirdPartyAd.setRevenueListener(null)
                thirdPartyAd.destroy()
                logInfo(TAG) {
                    "onAdLoadFailed: adUnitId=$adUnitId, error=${error.contentToString()}"
                }
                val code = error.code
                val message = error.message
                dispatchAdFailedLoadedEvent(code = code, message = message, poseidonConfigVersion = parameter.poseidonConfigVersion)
                loadCallback.onAdLoadFailed(code = code, message = message)
            }

            override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {
                maxOpenAdAtomic.get()
                    ?.dispatchAdFailedToShowEvent(maxAd = maxAd, error = error)
            }
        }
        thirdPartyAd.setListener(listener)
        markAdRequestStarted()
        thirdPartyAd.loadAd()
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = MaxSetup

    companion object {
        private const val TAG = "MaxOpenAdLoader"
    }

    private class MaxOpenAd(
        @AdSourceIdDef private val adSourceId: Int,
        private var thirdPartyAd: MaxAppOpenAd?,
        private var maxAd: MaxAd?,
        private val parameter: OpenParameter
    ) : OpenAdBase() {

        override fun getAdSourceId(): Int = adSourceId

        private val fullScreenContentCallbackAtomic: AtomicReference<InnerFullScreenContentCallback> =
            AtomicReference()

        init {
            updateEstimatedEcpmAdSourceInfo(maxAd.resolveAdSourceInfo())
        }

        override fun show(
            showParameter: PlatformOpenAdShowParameter,
            showConfig: InnerAdShowConfig
        ): ShowResultCallback {
            val thirdPartyAd = thirdPartyAd
                ?: return ShowResultCallback.Failure(
                    code = ERROR_CODE_NULL_THIRD_PARTY_AD,
                    message = "MaxOpenAd is null"
                )

            thirdPartyAd.setRevenueListener { maxAd ->
                val adSourceInfo = maxAd.resolveAdSourceInfo()
                updateEstimatedEcpmAdSourceInfo(adSourceInfo)
                val adPaidInfo = resolveAdPaidInfo(
                    maxAd = maxAd,
                    loadParameter = parameter,
                    adSourceInfo = adSourceInfo
                )
                logInfo(TAG) {
                    "setRevenueListener: maxAd=${maxAd.contentToString()}, adPaidInfo=$adPaidInfo"
                }
                dispatchOnPaidEvent(adPaidInfo)
            }

            return runCatching {
                thirdPartyAd.showAd()
            }.fold(
                onSuccess = { ShowResultCallback.Success },
                onFailure = { t ->
                    destroy()
                    ShowResultCallback.Failure(
                        code = ERROR_CODE_UNKNOWN,
                        message = t.message ?: "Failed to show MaxOpenAd"
                    )
                }
            )
        }

        override fun setFullScreenContentCallback(fullScreenContentCallback: InnerFullScreenContentCallback) {
            fullScreenContentCallbackAtomic.set(fullScreenContentCallback)
        }

        fun dispatchAdImpressionEvent(maxAd: MaxAd) {
            val adSourceInfo = maxAd.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            logInfo(TAG) {
                "Ad recorded an impression. maxAd=${maxAd.contentToString()}, adSourceInfo=${adSourceInfo}"
            }
            super.dispatchAdImpressionEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
            fullScreenContentCallbackAtomic.get()?.onAdShowedFullScreenContent()
        }

        fun dispatchAdFailedToShowEvent(maxAd: MaxAd, error: MaxError) {
            val adSourceInfo = maxAd.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            logInfo(TAG) {
                "Ad failed to show fullscreen content. maxAd=${maxAd.contentToString()}, error=${error.contentToString()}, adSourceInfo=${adSourceInfo}"
            }
            val code = error.code
            val message = error.message
            super.dispatchAdFailedToShowEvent(
                code = code,
                message = message,
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
            fullScreenContentCallbackAtomic.get()?.onAdFailedToShowFullScreenContent(
                code = code,
                message = message
            )
            destroy()
        }

        fun dispatchAdClickedEvent(maxAd: MaxAd) {
            val adSourceInfo = maxAd.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            logInfo(TAG) {
                "Ad was clicked. maxAd=${maxAd.contentToString()}, adSourceInfo=$adSourceInfo"
            }
            super.dispatchAdClickedEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
            fullScreenContentCallbackAtomic.get()?.onAdClicked()
        }

        fun dispatchAdClosedEvent(maxAd: MaxAd?) {
            if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
                return
            }
            val adSourceInfo = maxAd.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            logInfo(TAG) {
                "Ad dismissed fullscreen content. maxAd=${maxAd?.contentToString()}, adSourceInfo=$adSourceInfo"
            }
            super.dispatchAdClosedEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
            fullScreenContentCallbackAtomic.get()?.onAdDismissedFullScreenContent()
            runCatching {
                destroy()
            }
        }

        override fun onAdForceClosed() {
            dispatchAdClosedEvent(maxAd)
        }

        override fun isReady(): Boolean {
            return thirdPartyAd != null
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
            thirdPartyAd?.setListener(null)
            thirdPartyAd?.setRevenueListener(null)
            thirdPartyAd?.destroy()
            thirdPartyAd = null
            maxAd = null
            fullScreenContentCallbackAtomic.set(null)
        }

        companion object {
            private const val TAG = "maxOpenAd"
        }
    }
}
