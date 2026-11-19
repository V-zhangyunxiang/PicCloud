package com.poseidon.ad.mediation.topon.show

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.thinkup.core.api.TUAdInfo
import com.thinkup.nativead.api.TUNative
import com.thinkup.nativead.api.TUNativeAdView
import com.thinkup.nativead.api.TUNativeEventExListener
import com.thinkup.nativead.api.TUNativeImageView
import com.thinkup.nativead.api.TUNativeMaterial
import com.thinkup.nativead.api.TUNativePrepareInfo
import com.thinkup.nativead.api.NativeAd
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.dp2px
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.adinfo.custom.AdViewBinding
import com.poseidon.ad.main.adinfo.custom.NativeTemplateView
import com.poseidon.ad.main.adinfo.custom.makeAdLabelView
import com.poseidon.ad.main.adinfo.custom.maxMediaViewHeight
import com.poseidon.ad.main.load.NativeParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformNativeAdShowParameter
import com.poseidon.ad.mediation.topon.MultiImageView
import com.poseidon.ad.mediation.topon.resolveAdPaidInfo
import com.poseidon.ad.mediation.topon.toAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

internal class TopOnNativeAd(
    @AdSourceIdDef private val adSourceId: Int,
    private var thirdPartyAd: TUNative?,
    private val parameter: NativeParameter,
    initialAdInfo: TUAdInfo? = null,
) : NativeAdBase() {

    private val adListenerAtomic = AtomicReference<AdShowListener>()
    private val nativeAdAtomic = AtomicReference<NativeAd>()
    private val latestAdInfoAtomic = AtomicReference(initialAdInfo)

    init {
        rememberAdInfo(initialAdInfo)
    }

    override fun getAdSourceId(): Int = adSourceId

    override fun setAdListener(listener: AdShowListener) {
        adListenerAtomic.set(listener)
    }

    override fun render(showParameter: PlatformNativeAdShowParameter): ShowResultCallback {
        val thirdPartyAd = thirdPartyAd
            ?: return ShowResultCallback.Failure(
                code = ERROR_CODE_NULL_THIRD_PARTY_AD,
                message = ERROR_MESSAGE_NULL_NATIVE
            )
        val nativeAd = thirdPartyAd.nativeAd
            ?: return ShowResultCallback.Failure(
                code = ERROR_CODE_NULL_THIRD_PARTY_AD,
                message = ERROR_MESSAGE_NULL_NATIVE_AD
            )
        configureRevenueListener(nativeAd)
        nativeAdAtomic.set(nativeAd)
        nativeAd.setNativeEventListener(createNativeEventListener())
        val templateView: NativeTemplateView = showParameter.templateView
        val atNativeAdView = createNativeAdView(templateView)
        val prepareInfo = TUNativePrepareInfo()
        if (nativeAd.isNativeExpress) {
            nativeAd.renderAdContainer(atNativeAdView, null)
        } else {
            renderSelfRenderAd(nativeAd, templateView, atNativeAdView, prepareInfo)
        }
        nativeAd.prepare(atNativeAdView, prepareInfo)
        return ShowResultCallback.Success
    }

    override fun onAdForceClosed() {
        dispatchAdCloseEvent()
    }

    override fun isReady(): Boolean = thirdPartyAd != null

    override fun getParameter(): Parameter = parameter

    override fun eCPM(): Double? {
        val adInfo = latestAdInfoAtomic.get()
        val adSourceInfo = adInfo?.toAdSourceInfo()
        return adInfo?.let { resolveAdPaidInfo(adInfo = it, loadParameter = parameter).eCPM }
            ?.takeIf { it > 0.0 }
            ?: estimatedEcpm(adSourceInfo)
    }

    override fun destroy() {
        logInfo(TAG) {
            "Call destroy!!, stack trace=${Thread.currentThread().stackTrace.contentToString()}"
        }
        adListenerAtomic.set(null)
        thirdPartyAd?.apply {
            setAdListener(null)
            setAdSourceStatusListener(null)
        }
        thirdPartyAd = null
        nativeAdAtomic.get()?.apply {
            setNativeEventListener(null)
            destory()
        }
        nativeAdAtomic.set(null)
        latestAdInfoAtomic.set(null)
    }

    private fun configureRevenueListener(nativeAd: NativeAd) {
        nativeAd.setAdRevenueListener { adInfo ->
            rememberAdInfo(adInfo)
            val adPaidInfo = resolveAdPaidInfo(adInfo = adInfo, loadParameter = parameter)
            logInfo(TAG) {
                "onAdRevenue: adInfo = $adInfo, adPaidInfo=$adPaidInfo"
            }
            dispatchOnPaidEvent(adPaidInfo)
        }
    }

    private fun createNativeEventListener(): TUNativeEventExListener {
        return object : TUNativeEventExListener {
            override fun onAdImpressed(view: TUNativeAdView, info: TUAdInfo) {
                rememberAdInfo(info)
                val adSourceInfo = info.toAdSourceInfo()
                dispatchAdImpressionEvent(
                    adSourceId = adSourceInfo.adSourceId,
                    adSourcePlacementId = adSourceInfo.adSourcePlacementId,
                    adSourceName = info.networkName
                )
                adListenerAtomic.get().onAdShowSuccess()
            }

            override fun onAdClicked(view: TUNativeAdView, info: TUAdInfo) {
                rememberAdInfo(info)
                val adSourceInfo = info.toAdSourceInfo()
                dispatchAdClickedEvent(
                    adSourceId = adSourceInfo.adSourceId,
                    adSourcePlacementId = adSourceInfo.adSourcePlacementId,
                    adSourceName = info.networkName
                )
                adListenerAtomic.get().onAdClicked()
            }

            override fun onAdVideoStart(view: TUNativeAdView) {
                // no-op
            }

            override fun onAdVideoEnd(view: TUNativeAdView) {
                // no-op
            }

            override fun onAdVideoProgress(view: TUNativeAdView, progress: Int) {
                // no-op
            }

            override fun onDeeplinkCallback(view: TUNativeAdView, info: TUAdInfo, isSuccess: Boolean) {
                // no-op
            }

            override fun onAdActRewardSuccess(info: TUAdInfo) {
                // Native activity rewards are not exposed by the current show listener.
            }

            override fun onAdActReward(info: TUAdInfo, status: Int) {
                // Native activity rewards are not exposed by the current show listener.
            }
        }
    }

    /** 统一创建 TopOn 原生容器，负责 attach 监听和关闭派发 */
    private fun createNativeAdView(templateView: NativeTemplateView): TUNativeAdView {
        val context = templateView.context
        val atNativeAdView = TUNativeAdView(context)
        atNativeAdView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                logInfo(TAG) {
                    "TopOnNativeAd.render.onViewAttachedToWindow: $v"
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                logInfo(TAG) {
                    "TopOnNativeAd.render.onViewDetachedFromWindow: $v"
                }
                atNativeAdView.removeOnAttachStateChangeListener(this)
                dispatchAdCloseEvent()
            }
        })
        templateView.removeAllViews()
        templateView.addView(
            atNativeAdView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return atNativeAdView
    }

    private fun renderSelfRenderAd(
        nativeAd: NativeAd,
        templateView: NativeTemplateView,
        atNativeAdView: TUNativeAdView,
        prepareInfo: TUNativePrepareInfo
    ) {
        val material = nativeAd.adMaterial
        val binding = templateView.inflateAdContentViewTo(atNativeAdView)
        val hasMediaView = material.getAdMediaView(binding.mediaView) != null
        logInfo(TAG) {
            buildString {
                append("Render TopOn native: scene=${parameter.sceneName}, ")
                append("title=${material.title}, desc=${material.descriptionText}, ")
                append("iconUrl=${material.iconImageUrl}, mainImageUrl=${material.mainImageUrl}, ")
                append("imageList=${material.imageUrlList}, mainImageSize=${material.mainImageWidth}x${material.mainImageHeight}, ")
                append("hasMediaView=$hasMediaView, isNativeExpress=${nativeAd.isNativeExpress}, ")
                append("adLogoView=${material.adLogoView}, adChoiceIconUrl=${material.adChoiceIconUrl}")
            }
        }
        binding.bindSelfRenderView(
            context = templateView.context,
            material = material,
            prepareInfo = prepareInfo
        )
        templateView.addView(
            makeAdLabelView(templateView.context),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp2px(templateView.context, 5F).toInt()
                topMargin = dp2px(templateView.context, 5F).toInt()
                marginEnd = dp2px(templateView.context, 5F).toInt()
                bottomMargin = dp2px(templateView.context, 5F).toInt()
            }
        )
        nativeAd.renderAdContainer(atNativeAdView, binding.rootView)
    }

    private fun AdViewBinding.bindSelfRenderView(
        context: Context,
        material: TUNativeMaterial,
        prepareInfo: TUNativePrepareInfo
    ) {
        val clickViewList = arrayListOf<View>()
        val title = material.title
        if (title.isNullOrEmpty()) {
            titleView.isVisible = false
        } else {
            titleView.text = title
            prepareInfo.titleView = titleView
            clickViewList.add(titleView)
            titleView.isVisible = true
        }
        val descriptionText = material.descriptionText
        if (descriptionText.isNullOrEmpty()) {
            descriptionView.isVisible = false
        } else {
            descriptionView.text = descriptionText
            prepareInfo.descView = descriptionView
            clickViewList.add(descriptionView)
            descriptionView.isVisible = true
        }
        val atAdIconView: View? = material.adIconView
        val iconImageUrl: String? = material.iconImageUrl
        adIconView.removeAllViews()
        if (atAdIconView != null) {
            adIconView.addView(
                atAdIconView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            prepareInfo.iconView = atAdIconView
            clickViewList.add(atAdIconView)
            adIconView.isVisible = true
        } else if (!TextUtils.isEmpty(iconImageUrl)) {
            val iconView = TUNativeImageView(context)
            adIconView.addView(
                iconView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            iconView.setImage(iconImageUrl)
            prepareInfo.iconView = iconView
            clickViewList.add(iconView)
            adIconView.isVisible = true
        } else {
            adIconView.visibility = View.GONE
        }
        val atMediaView: View? = material.getAdMediaView(mediaView)
        val mainImageHeight: Int = material.mainImageHeight
        val mainImageWidth: Int = material.mainImageWidth
        val mainImageParam = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            maxMediaViewHeight(context)
        )
        if (atMediaView == null) {
            val viewTreeObserver: ViewTreeObserver = rootView.viewTreeObserver
            viewTreeObserver.addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val realMainImageWidth: Int = rootView.width - dp2px(context, 10F).toInt()
                        if (mainImageWidth > 0 && mainImageHeight > 0 && mainImageWidth > mainImageHeight) {
                            val realMainHeight =
                                realMainImageWidth * mainImageHeight / mainImageWidth
                            mainImageParam.width = realMainImageWidth
                            mainImageParam.height = realMainHeight
                        } else {
                            mainImageParam.width = FrameLayout.LayoutParams.MATCH_PARENT
                            mainImageParam.height = realMainImageWidth * 600 / 1024
                        }
                    }
                }
            )
        } else {
            var realMainImageWidth: Int =
                context.resources.displayMetrics.widthPixels - dp2px(context, 10F).toInt()
            if (context.resources.displayMetrics.widthPixels > context.resources.displayMetrics.heightPixels) {
                realMainImageWidth =
                    context.resources.displayMetrics.widthPixels -
                        dp2px(context, 10F).toInt() -
                        dp2px(context, 330F).toInt() -
                        dp2px(context, 130F).toInt()
            }
            if (mainImageWidth > 0 && mainImageHeight > 0 && mainImageWidth > mainImageHeight) {
                mainImageParam.width = FrameLayout.LayoutParams.MATCH_PARENT
                mainImageParam.height = realMainImageWidth * mainImageHeight / mainImageWidth
            } else {
                mainImageParam.width = FrameLayout.LayoutParams.MATCH_PARENT
                mainImageParam.height = realMainImageWidth * 600 / 1024
            }
        }
        val imageList: List<String>? = material.imageUrlList
        mediaView.removeAllViews()
        if (atMediaView != null) {
            if (atMediaView.parent != null) {
                (atMediaView.parent as ViewGroup).removeView(atMediaView)
            }
            mainImageParam.gravity = Gravity.CENTER
            atMediaView.layoutParams = mainImageParam
            mediaView.addView(atMediaView, mainImageParam)
            clickViewList.add(atMediaView)
            mediaView.visibility = View.VISIBLE
        } else if (imageList != null && imageList.size > 1) {
            val multiImageView = MultiImageView(context)
            multiImageView.setImageList(imageList, mainImageWidth, mainImageHeight)
            prepareInfo.mainImageView = multiImageView
            mediaView.addView(
                multiImageView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    maxMediaViewHeight(context)
                )
            )
            clickViewList.add(multiImageView)
        } else if (!TextUtils.isEmpty(material.mainImageUrl)) {
            val imageView = TUNativeImageView(context)
            imageView.setImage(material.mainImageUrl)
            imageView.layoutParams = mainImageParam
            mediaView.addView(imageView, mainImageParam)
            prepareInfo.mainImageView = imageView
            clickViewList.add(imageView)
            mediaView.visibility = View.VISIBLE
        } else {
            mediaView.removeAllViews()
            mediaView.visibility = View.GONE
        }
        adLogoView.removeAllViews()
        val atAdLogoView: View? = material.adLogoView
        if (atAdLogoView != null) {
            adLogoView.visibility = View.VISIBLE
            adLogoView.addView(atAdLogoView)
        } else {
            val adChoiceIconUrl: String? = material.adChoiceIconUrl
            val adLogoBitmap: Bitmap? = material.adLogo
            if (!TextUtils.isEmpty(adChoiceIconUrl)) {
                val logoView = TUNativeImageView(context)
                logoView.setImage(adChoiceIconUrl)
                prepareInfo.adLogoView = logoView
                adLogoView.addView(
                    logoView,
                    dp2px(context, 40f).toInt(),
                    dp2px(context, 20f).toInt()
                )
                adLogoView.visibility = View.VISIBLE
            } else if (adLogoBitmap != null) {
                val logoView = TUNativeImageView(context)
                logoView.setImageBitmap(adLogoBitmap)
                adLogoView.addView(
                    logoView,
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        40F,
                        context.resources.displayMetrics
                    ).toInt(),
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        20F,
                        context.resources.displayMetrics
                    ).toInt()
                )
                adLogoView.visibility = View.VISIBLE
            } else {
                adLogoView.visibility = View.GONE
            }
        }
        val callToActionText = material.callToActionText
        if (callToActionText.isNullOrEmpty()) {
            callToActionView.isVisible = false
        } else {
            callToActionView.text = callToActionText
            prepareInfo.ctaView = callToActionView
            clickViewList.add(callToActionView)
            callToActionView.isVisible = true
        }
        prepareInfo.clickViewList = clickViewList
    }

    private fun dispatchAdCloseEvent() {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        adListenerAtomic.get().onAdClosed()
        val info: TUAdInfo? = latestAdInfoAtomic.get()
        val adSourceInfo = info?.toAdSourceInfo()
        dispatchAdClosedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = info?.networkName
        )
        runCatching {
            destroy()
        }
    }

    private fun rememberAdInfo(info: TUAdInfo?) {
        if (info != null) {
            latestAdInfoAtomic.set(info)
            updateEstimatedEcpmAdSourceInfo(info.toAdSourceInfo())
        }
    }

    companion object {
        private const val TAG = "TopOnNativeAd"
        private const val ERROR_MESSAGE_NULL_NATIVE = "TUNative is null."
        private const val ERROR_MESSAGE_NULL_NATIVE_AD = "TopOn NativeAd is null."
    }
}
