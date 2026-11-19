package com.poseidon.ad.mediation.topon.show

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.TUShowConfig
import com.thinkup.splashad.api.TUSplashAd
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.databinding.ActivityFullscreenAdHostBinding
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.ERROR_CODE_THIRD_PARTY_AD_NOT_READY
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.OpenAdBase
import com.poseidon.ad.main.load.OpenParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformOpenAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.mediation.topon.resolveAdPaidInfo
import com.poseidon.ad.mediation.topon.TopOnAdmobExtractor
import com.poseidon.ad.mediation.topon.toAdSourceInfo
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * TopOn 开屏广告包装类，负责展示、事件派发与回调。
 */
internal class TopOnOpenAd(
    @AdSourceIdDef private val adSourceId: Int,
    private var thirdPartyAd: TUSplashAd?,
    private val parameter: OpenParameter,
    private val bidExtractionResult: TopOnAdmobExtractor.BidExtractionResult? = null,
    initialAdInfo: TUAdInfo? = null,
) : OpenAdBase() {

    /** 全屏内容回调引用，用于保障生命周期 */
    private val fullScreenContentCallbackAtomic = AtomicReference<InnerFullScreenContentCallback>()
    private val latestAdInfoAtomic = AtomicReference(initialAdInfo)

    init {
        rememberAdInfo(initialAdInfo)
    }

    override fun getAdSourceId(): Int = adSourceId

    override fun show(
        showParameter: PlatformOpenAdShowParameter,
        showConfig: InnerAdShowConfig
    ): ShowResultCallback {
        val currentAd = thirdPartyAd ?: return ShowResultCallback.Failure(
            code = ERROR_CODE_NULL_THIRD_PARTY_AD,
            message = "TopOnOpenAd is null"
        )
        if (!isReady()) {
            return ShowResultCallback.Failure(
                code = ERROR_CODE_THIRD_PARTY_AD_NOT_READY,
                message = "TopOnOpenAd is not ready"
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
        return TopOnOpenActivity.start(
            context = showParameter.activity,
            sceneName = showConfig.sceneName,
            thirdPartyAd = currentAd
        ).fold(
            onSuccess = { ShowResultCallback.Success },
            onFailure = { t ->
                currentAd.setAdRevenueListener(null)
                ShowResultCallback.Failure(
                    code = ERROR_CODE_UNKNOWN,
                    message = t.message ?: "Failed to start TopOnOpenActivity"
                )
            }
        )
    }

    override fun setFullScreenContentCallback(fullScreenContentCallback: InnerFullScreenContentCallback) {
        fullScreenContentCallbackAtomic.set(fullScreenContentCallback)
    }

    fun dispatchAdImpressionEvent(info: TUAdInfo) {
        rememberAdInfo(info)
        val adSourceInfo = info.toAdSourceInfo()
        super.dispatchAdImpressionEvent(
            adSourceId = adSourceInfo.adSourceId,
            adSourcePlacementId = adSourceInfo.adSourcePlacementId,
            adSourceName = info.networkName
        )
        fullScreenContentCallbackAtomic.get()?.onAdShowedFullScreenContent()
    }

    fun dispatchAdClickedEvent(info: TUAdInfo) {
        rememberAdInfo(info)
        val adSourceInfo = info.toAdSourceInfo()
        super.dispatchAdClickedEvent(
            adSourceId = adSourceInfo.adSourceId,
            adSourcePlacementId = adSourceInfo.adSourcePlacementId,
            adSourceName = info.networkName
        )
        fullScreenContentCallbackAtomic.get()?.onAdClicked()
    }

    fun dispatchAdClosedEvent(info: TUAdInfo?) {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        TopOnOpenActivity.finish()
        val effectiveInfo = info ?: latestAdInfoAtomic.get()
        rememberAdInfo(effectiveInfo)
        val adSourceInfo = effectiveInfo?.toAdSourceInfo()
        super.dispatchAdClosedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = effectiveInfo?.networkName
        )
        fullScreenContentCallbackAtomic.get()?.onAdDismissedFullScreenContent()
        runCatching {
            destroy()
        }
    }

    override fun onAdForceClosed() {
        dispatchAdClosedEvent(null)
    }

    override fun isReady(): Boolean = thirdPartyAd != null

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
        thirdPartyAd?.setAdListener(null)
        thirdPartyAd?.setAdRevenueListener(null)
        thirdPartyAd = null
        fullScreenContentCallbackAtomic.set(null)
        latestAdInfoAtomic.set(null)
    }

    private fun rememberAdInfo(info: TUAdInfo?) {
        if (info != null) {
            latestAdInfoAtomic.set(info)
            updateEstimatedEcpmAdSourceInfo(info.toAdSourceInfo())
        }
    }

    companion object {
        private const val TAG = "TopOnOpenAd"
    }
}

/**
 * TopOn 开屏广告展示 Activity，负责承载第三方广告并控制生命周期。
 */
class TopOnOpenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        topOnActivityWeakRef = WeakReference(this)

        val decorView: View = window.decorView
        decorView.systemUiVisibility =
            decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val binding = ActivityFullscreenAdHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val thirdPartyAd = thirdPartyAdAtomic.getAndSet(null)
        val sceneName = intent.getStringExtra(ARG_SCENE_NAME).orEmpty()
        if (thirdPartyAd == null) {
            finish()
            return
        } else {
            thirdPartyAd.show(
                this,
                binding.fullscreenAdHolder,
                null,
                TUShowConfig.Builder()
                    .scenarioId(sceneName)
                    .build()
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearPendingAd()
        topOnActivityWeakRef?.clear()
        topOnActivityWeakRef = null
    }

    companion object {
        private var topOnActivityWeakRef: WeakReference<TopOnOpenActivity>? = null
        /** 缓存第三方开屏广告实体，确保跨 Activity 切换时不丢失 */
        private val thirdPartyAdAtomic: AtomicReference<TUSplashAd> = AtomicReference()
        private const val ARG_SCENE_NAME = "sceneName"

        internal fun finish() {
            topOnActivityWeakRef?.get()?.finish()
        }

        internal fun start(context: Context, sceneName: String, thirdPartyAd: TUSplashAd): Result<Unit> {
            clearPendingAd()
            thirdPartyAdAtomic.set(thirdPartyAd)
            return runCatching {
                val intent = Intent(context, TopOnOpenActivity::class.java).apply {
                    putExtra(ARG_SCENE_NAME, sceneName)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
            }.onFailure {
                if (thirdPartyAdAtomic.compareAndSet(thirdPartyAd, null)) {
                    clearAdListeners(thirdPartyAd)
                }
            }
        }

        private fun clearPendingAd() {
            thirdPartyAdAtomic.getAndSet(null)?.let(::clearAdListeners)
        }

        private fun clearAdListeners(ad: TUSplashAd) {
            ad.setAdListener(null)
            ad.setAdRevenueListener(null)
        }
    }
}
