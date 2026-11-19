package com.poseidon.ad.mediation.topon.load

import android.view.ViewGroup
import android.widget.FrameLayout
import com.thinkup.banner.api.TUBannerListener
import com.thinkup.banner.api.TUBannerView
import com.thinkup.core.api.TUAdConst
import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.AdError
import com.poseidon.ad.common.toolkit.dp2px
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.postOnMainThread
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.BannerAdBase
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.load.BannerAdLoad
import com.poseidon.ad.main.load.BannerParameter
import com.poseidon.ad.main.load.InnerAdLoadAndShowCallback
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.processor.PlatformBannerAdLoadAndShowParameter
import com.poseidon.ad.mediation.topon.init.TopOnSetup
import com.poseidon.ad.mediation.topon.TopOnStatusDispatcher
import com.poseidon.ad.mediation.topon.resolveAdPaidInfo
import com.poseidon.ad.mediation.topon.toAdSourceInfo
import com.poseidon.ad.mediation.topon.show.TopOnBannerAd
import java.util.concurrent.atomic.AtomicReference

internal class TopOnBannerLoad(parameter: BannerParameter) :
    BannerAdLoad(parameter = parameter) {

    /** 强类型 Banner 参数，便于后续使用 */
    private val bannerParameter: BannerParameter = parameter

    /** 当前 BannerView 的引用，用于销毁 */
    private val adViewAtomic = AtomicReference<TUBannerView>()

    override fun mediationAdLoad(
        loadParameter: PlatformBannerAdLoadAndShowParameter,
        loadCallback: InnerAdLoadAndShowCallback<BannerAdBase>
    ) {
        val adView = createBannerView(loadParameter)
        val topOnBannerAd = createBannerWrapper(loadCallback)
        attachBannerView(loadParameter, adView)
        configureBannerListeners(adView, topOnBannerAd, loadCallback)
        configureBannerSize(loadParameter, adView)
        // 此处是TopOn的Banner加载的核心方法：TUBannerView.loadAd()
        markAdRequestStarted()
        adView.loadAd()
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = TopOnSetup

    override fun destroy() {
        logInfo(TAG) { "destroy" }
        adViewAtomic.getAndSet(null)?.destroy()
    }

    /** 构建并初始化 BannerView */
    private fun createBannerView(loadParameter: PlatformBannerAdLoadAndShowParameter): TUBannerView {
        val context = loadParameter.context
        val width = context.resources.displayMetrics.widthPixels
        val height = ViewGroup.LayoutParams.WRAP_CONTENT
        return TUBannerView(context).apply {
            setPlacementId(bannerParameter.adUnitId)
            layoutParams = FrameLayout.LayoutParams(width, height)
            adViewAtomic.set(this)
        }
    }

    /** 创建 TopOn Banner 的包装对象 */
    private fun createBannerWrapper(loadCallback: InnerAdLoadAndShowCallback<BannerAdBase>): TopOnBannerAd {
        return TopOnBannerAd(
            parameter = bannerParameter,
            listener = loadCallback
        )
    }

    /** 将 BannerView 挂载到容器中 */
    private fun attachBannerView(
        loadParameter: PlatformBannerAdLoadAndShowParameter,
        adView: TUBannerView
    ) {
        loadParameter.adViewContainer.apply {
            removeAllViews()
            addView(adView)
        }
    }

    /** 绑定收入监听与 Banner 事件监听 */
    private fun configureBannerListeners(
        adView: TUBannerView,
        topOnBannerAd: TopOnBannerAd,
        loadCallback: InnerAdLoadAndShowCallback<BannerAdBase>
    ) {
        adView.setAdRevenueListener { adInfo ->
            val adPaidInfo = resolveAdPaidInfo(adInfo = adInfo, loadParameter = bannerParameter)
            logInfo(TAG) {
                "onAdRevenue: adInfo = $adInfo, adPaidInfo=$adPaidInfo"
            }
            topOnBannerAd.publicDispatchOnPaidEvent(adPaidInfo)
        }
        adView.setBannerAdListener(
            TopOnBannerListener(
                adView = adView,
                bannerAd = topOnBannerAd,
                loadCallback = loadCallback
            )
        )
    }

    /** 配置 Banner 广告尺寸等本地参数 */
    private fun configureBannerSize(
        loadParameter: PlatformBannerAdLoadAndShowParameter,
        adView: TUBannerView
    ) {
        val context = loadParameter.context
        adView.setLocalExtra(
            mutableMapOf<String, Any>(
                TUAdConst.KEY.AD_WIDTH to dp2px(context = context, value = BANNER_WIDTH_DP).toInt(),
                TUAdConst.KEY.AD_HEIGHT to dp2px(context = context, value = BANNER_HEIGHT_DP).toInt()
            )
        )
    }

    /** 处理 Banner 广告生命周期事件的监听器 */
    private inner class TopOnBannerListener(
        private val adView: TUBannerView,
        private val bannerAd: TopOnBannerAd,
        private val loadCallback: InnerAdLoadAndShowCallback<BannerAdBase>
    ) : TUBannerListener {

        override fun onBannerLoaded() {
            markAdRequestReturned()
            TopOnStatusDispatcher.dispatch {
                val atTopAdInfo: TUAdInfo? = runCatching {
                    adView.checkAdStatus().getTUTopAdInfo()
                }.getOrNull()
                postOnMainThread {
                    logInfo(TAG) {
                        "onBannerLoaded: atTopAdInfo=$atTopAdInfo"
                    }
                    handleBannerLoaded(atTopAdInfo)
                }
            }
        }

        override fun onBannerFailed(error: AdError?) {
            markAdRequestReturned()
            logInfo(TAG) {
                "onBannerFailed: error=$error"
            }
            handleBannerLoadFailed(error)
        }

        override fun onBannerClicked(atAdInfo: TUAdInfo?) {
            logInfo(TAG) {
                "onBannerClicked: atAdInfo=$atAdInfo"
            }
            bannerAd.dispatchAdClickedEvent(info = atAdInfo)
        }

        override fun onBannerShow(atAdInfo: TUAdInfo?) {
            // only call back once
            logInfo(TAG) {
                "onBannerShow: atAdInfo=$atAdInfo"
            }
            bannerAd.dispatchAdImpressionEvent(info = atAdInfo)
        }

        override fun onBannerClose(atAdInfo: TUAdInfo?) {
            logInfo(TAG) {
                "onBannerClose: atAdInfo=$atAdInfo"
            }
            bannerAd.dispatchAdClosedEvent(info = atAdInfo)
        }

        override fun onBannerAutoRefreshed(atAdInfo: TUAdInfo?) {
            logInfo(TAG) {
                "onBannerAutoRefreshed: atAdInfo=$atAdInfo"
            }
            handleBannerLoaded(atAdInfo, isAutoRefresh = true)
        }

        override fun onBannerAutoRefreshFail(error: AdError?) {
            logInfo(TAG) {
                "onBannerAutoRefreshFail: error=$error"
            }
            handleBannerLoadFailed(error, isAutoRefresh = true)
        }

        /** 统一处理加载成功逻辑 */
        private fun handleBannerLoaded(atAdInfo: TUAdInfo?, isAutoRefresh: Boolean = false) {
            val adSourceInfo = atAdInfo?.toAdSourceInfo()
            val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
            val revenue = atAdInfo?.let {
                resolveAdPaidInfo(adInfo = it, loadParameter = bannerParameter).revenue
            }
            dispatchAdLoadedEvent(
                adSourceId = adSourceId,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                revenue = revenue,
                poseidonConfigVersion = bannerParameter.poseidonConfigVersion,
                adSourceName = atAdInfo?.networkName,
                estimatedRevenue = -1.0,
                isAutoRefresh = isAutoRefresh
            )
            bannerAd.updateAdSourceId(adSourceId)
            loadCallback.onAdLoadSuccess(bannerAd)
        }

        /** 统一处理加载失败逻辑 */
        private fun handleBannerLoadFailed(error: AdError?, isAutoRefresh: Boolean = false) {
            val code = error?.code?.toIntOrNull() ?: ERROR_CODE_UNKNOWN
            val message = error?.desc.orEmpty()
            dispatchAdFailedLoadedEvent(
                code = code,
                message = message,
                poseidonConfigVersion = bannerParameter.poseidonConfigVersion,
                isAutoRefresh = isAutoRefresh
            )
            loadCallback.onAdLoadFailed(code = code, message = message)
        }
    }

    companion object {
        private const val TAG = "TopOnBannerAdLoader"
        private const val BANNER_WIDTH_DP = 320F
        private const val BANNER_HEIGHT_DP = 50F
    }
}
