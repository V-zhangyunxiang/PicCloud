package com.poseidon.ad.mediation.topon.show

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.ERROR_CODE_THIRD_PARTY_AD_NOT_READY
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.InterstitialAdBase
import com.poseidon.ad.main.load.InterstitialParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformInterstitialAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.mediation.topon.TopOnAdmobExtractor
import com.poseidon.ad.mediation.topon.resolveAdPaidInfo
import com.poseidon.ad.mediation.topon.toAdSourceInfo
import com.thinkup.core.api.AdError
import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.TUShowConfig
import com.thinkup.interstitial.api.TUInterstitial
import java.util.concurrent.atomic.AtomicReference

@Suppress("SpellCheckingInspection")
internal class TopOnInterstitialAd(
    @AdSourceIdDef private val adSourceId: Int,
    private var topOnInterstitialAd: TUInterstitial?,
    private val parameter: InterstitialParameter,
    private val bidExtractionResult: TopOnAdmobExtractor.BidExtractionResult? = null,
    initialAdInfo: TUAdInfo? = null,
) : InterstitialAdBase() {

    /** 全屏内容回调引用，保证生命周期一致 */
    private val fullScreenContentCallbackAtomic = AtomicReference<InnerFullScreenContentCallback>()
    private val hostDestroyObserverAtomic = AtomicReference<DefaultLifecycleObserver>()
    private val hostLifecycleOwnerAtomic = AtomicReference<LifecycleOwner>()
    private val latestAdInfoAtomic = AtomicReference(initialAdInfo)

    init {
        rememberAdInfo(initialAdInfo)
    }

    override fun getAdSourceId(): Int = adSourceId

    override fun show(
        showParameter: PlatformInterstitialAdShowParameter,
        showConfig: InnerAdShowConfig
    ): ShowResultCallback {
        val currentAd = topOnInterstitialAd ?: return ShowResultCallback.Failure(
            code = ERROR_CODE_NULL_THIRD_PARTY_AD,
            message = ERROR_MESSAGE_NULL_AD
        )
        if (!isReady()) {
            return ShowResultCallback.Failure(
                code = ERROR_CODE_THIRD_PARTY_AD_NOT_READY,
                message = ERROR_MESSAGE_NOT_READY
            )
        }
        currentAd.setAdRevenueListener { adInfo ->
            rememberAdInfo(adInfo)
            val adPaidInfo = resolveAdPaidInfo(adInfo = adInfo, loadParameter = parameter)
            logInfo(TAG) {
                "onAdRevenue: adInfo = $adInfo, adPaidInfo=$adPaidInfo"
            }
            dispatchOnPaidEvent(adPaidInfo)
        }
        observeHostDestroy(showParameter.activity)
        currentAd.show(
            showParameter.activity,
            TUShowConfig.Builder()
                .scenarioId(showConfig.sceneName)
                .build()
        )
        return ShowResultCallback.Success
    }

    override fun setFullScreenContentCallback(fullScreenContentCallback: InnerFullScreenContentCallback) {
        fullScreenContentCallbackAtomic.set(fullScreenContentCallback)
    }

    /** 派发展示曝光事件并触达全屏回调 */
    fun dispatchAdImpressionEvent(info: TUAdInfo) {
        rememberAdInfo(info)
        val adSourceInfo = info.toAdSourceInfo()
        super.dispatchAdImpressionEvent(
            adSourceId = adSourceInfo.adSourceId,
            adSourcePlacementId = adSourceInfo.adSourcePlacementId,
            adSourceName = info.networkName
        )
        notifyFullScreen { onAdShowedFullScreenContent() }
    }

    /** 派发展示失败事件并触达全屏回调 */
    fun dispatchAdFailedToShowEvent(error: AdError, info: TUAdInfo?) {
        val effectiveInfo = info ?: latestAdInfoAtomic.get()
        rememberAdInfo(effectiveInfo)
        val adSourceInfo = effectiveInfo?.toAdSourceInfo()
        val code = error.code.toIntOrNull() ?: ERROR_CODE_UNKNOWN
        val message = error.desc
        super.dispatchAdFailedToShowEvent(
            code = code,
            message = message,
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = effectiveInfo?.networkName
        )
        notifyFullScreen {
            onAdFailedToShowFullScreenContent(code = code, message = message)
        }
    }

    /** 派发点击事件并触达全屏回调 */
    fun dispatchAdClickedEvent(info: TUAdInfo) {
        rememberAdInfo(info)
        val adSourceInfo = info.toAdSourceInfo()
        super.dispatchAdClickedEvent(
            adSourceId = adSourceInfo.adSourceId,
            adSourcePlacementId = adSourceInfo.adSourcePlacementId,
            adSourceName = info.networkName
        )
        notifyFullScreen { onAdClicked() }
    }

    /** 派发关闭事件并触达全屏回调 */
    fun dispatchAdClosedEvent(info: TUAdInfo?) {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        val effectiveInfo = info ?: latestAdInfoAtomic.get()
        rememberAdInfo(effectiveInfo)
        val adSourceInfo = effectiveInfo?.toAdSourceInfo()
        super.dispatchAdClosedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = effectiveInfo?.networkName
        )
        notifyFullScreen { onAdDismissedFullScreenContent() }
        runCatching {
            destroy()
        }
    }

    override fun onAdForceClosed() {
        dispatchAdClosedEvent(null)
    }

    override fun isReady(): Boolean = topOnInterstitialAd != null

    override fun getParameter(): Parameter = parameter

    override fun eCPM(): Double? {
        val adInfo = latestAdInfoAtomic.get()
        val adSourceInfo = adInfo?.toAdSourceInfo()
        return adInfo?.let { resolveAdPaidInfo(adInfo = it, loadParameter = parameter).eCPM }
            ?.takeIf { it > 0.0 }
            ?: bidExtractionResult?.winnerEcpm?.takeIf { it > 0.0 }
            ?: estimatedEcpm(adSourceInfo)
    }

    override fun destroy() {
        doDestroy()
    }

    private fun doDestroy(destroyAd: Boolean = true) {
        clearHostDestroyObserver()
        if (destroyAd) {
            topOnInterstitialAd?.setAdListener(null)
            topOnInterstitialAd?.setAdRevenueListener(null)
            topOnInterstitialAd = null
        }
        fullScreenContentCallbackAtomic.set(null)
        latestAdInfoAtomic.set(null)
    }

    private fun rememberAdInfo(info: TUAdInfo?) {
        if (info != null) {
            latestAdInfoAtomic.set(info)
            updateEstimatedEcpmAdSourceInfo(info.toAdSourceInfo())
        }
    }

    /** 安全触发全屏内容回调 */
    private fun notifyFullScreen(block: InnerFullScreenContentCallback.() -> Unit) {
        fullScreenContentCallbackAtomic.get()?.block()
    }

    private fun observeHostDestroy(owner: LifecycleOwner) {
        clearHostDestroyObserver()
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                hostDestroyObserverAtomic.compareAndSet(this, null)
                hostLifecycleOwnerAtomic.compareAndSet(owner, null)
                doDestroy(false)
            }
        }
        hostLifecycleOwnerAtomic.set(owner)
        hostDestroyObserverAtomic.set(observer)
        owner.lifecycle.addObserver(observer)
    }

    private fun clearHostDestroyObserver() {
        val observer = hostDestroyObserverAtomic.getAndSet(null) ?: return
        hostLifecycleOwnerAtomic.getAndSet(null)?.lifecycle?.removeObserver(observer)
    }

    companion object {
        private const val TAG = "TopOnInterstitialAd"
        private const val ERROR_MESSAGE_NULL_AD = "TopOnInterstitialAd is null"
        private const val ERROR_MESSAGE_NOT_READY = "TopOnInterstitialAd is not ready"
    }
}
