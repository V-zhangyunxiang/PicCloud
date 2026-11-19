package com.poseidon.ad.main.show.mixed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.api.OnUserEarnedRewardListener
import com.poseidon.ad.api.RewardItem
import com.poseidon.ad.databinding.ActivityNativeMixFullscreenBinding
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.processor.PlatformInterstitialAdShowParameter
import com.poseidon.ad.main.processor.PlatformNativeAdShowParameter
import com.poseidon.ad.main.processor.PlatformOpenAdShowParameter
import com.poseidon.ad.main.processor.PlatformRewardedAdShowParameter
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

fun showNativeMixInterstitialAd(
    platformAdShowParameter: PlatformInterstitialAdShowParameter,
    ad: NativeAdBase,
    callback: InnerFullScreenContentCallback
) {
    val sceneName = platformAdShowParameter.sceneName
    val activity = platformAdShowParameter.activity
    NativeMixInterstitialAdActivity.start(
        context = activity,
        sceneName = sceneName,
        ad = ad,
        callback = callback,
    )
}

fun showNativeMixOpenAd(
    platformAdShowParameter: PlatformOpenAdShowParameter,
    ad: NativeAdBase,
    callback: InnerFullScreenContentCallback
) {
    val sceneName = platformAdShowParameter.sceneName
    val activity = platformAdShowParameter.activity
    NativeMixOpenAdActivity.start(
        context = activity,
        sceneName = sceneName,
        ad = ad,
        callback = callback,
    )
}

fun showNativeMixRewardedAd(
    platformAdShowParameter: PlatformRewardedAdShowParameter,
    ad: NativeAdBase,
    callback: InnerFullScreenContentCallback,
    onUserEarnedRewardListener: OnUserEarnedRewardListener
) {
    val sceneName = platformAdShowParameter.sceneName
    val activity = platformAdShowParameter.activity
    NativeMixRewardedAdActivity.start(
        context = activity,
        sceneName = sceneName,
        ad = ad,
        callback = callback,
        onUserEarnedRewardListener = onUserEarnedRewardListener,
    )
}

abstract class NativeMixFullscreenAdActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val decorView: View = window.decorView
        decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val binding = ActivityNativeMixFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.closeIcon) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())

            val top: Int = insets.top
            val bottom: Int = insets.bottom
            val height = abs(bottom - top)

            view.updateLayoutParams<RelativeLayout.LayoutParams> {
                topMargin += height
            }

            windowInsets
        }

        val nativeAd = when (this) {
            is NativeMixInterstitialAdActivity -> NativeMixInterstitialAdActivity.nativeAdAtomic.get()
            is NativeMixRewardedAdActivity -> NativeMixRewardedAdActivity.nativeAdAtomic.get()
            else -> null
        }
        val callback = when (this) {
            is NativeMixInterstitialAdActivity -> NativeMixInterstitialAdActivity.callbackAtomic.get()
            is NativeMixRewardedAdActivity -> NativeMixRewardedAdActivity.callbackAtomic.get()
            else -> null
        }

        val onUserEarnedRewardListener = when (this) {
            is NativeMixInterstitialAdActivity -> null
            is NativeMixRewardedAdActivity -> NativeMixRewardedAdActivity.onUserEarnedRewardListenerAtomic.get()
            else -> null
        }

        val sceneName = intent.getStringExtra(ARG_SCENE_NAME)
        if (nativeAd == null || callback == null || (this is NativeMixRewardedAdActivity && onUserEarnedRewardListener == null) || sceneName == null) {
            finish()
            return
        }

        binding.closeIcon.setOnClickListener {
            finish()
        }

        nativeAd.setAdListener(object : AdShowListener {
            override fun onAdClicked() {
                callback.onAdClicked()
            }

            override fun onAdShowSuccess() {
                callback.onAdShowedFullScreenContent()
            }

            override fun onAdClosed() {
                onUserEarnedRewardListener?.onUserEarnedReward(RewardItem.UNKNOWN)
                callback.onAdDismissedFullScreenContent()
                nativeAd.destroy()
            }

            override fun onAdShowFailed(code: Int, message: String) {
                callback.onAdFailedToShowFullScreenContent(code, message)
                nativeAd.destroy()
                finish()
            }
        })

        nativeAd.render(
            showParameter = PlatformNativeAdShowParameter(
                activity = this,
                sceneName = sceneName,
                templateView = binding.nativeTemplateView
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this is NativeMixInterstitialAdActivity) {
            NativeMixInterstitialAdActivity.nativeAdAtomic.set(null)
            NativeMixInterstitialAdActivity.callbackAtomic.set(null)
        } else if (this is NativeMixRewardedAdActivity) {
            NativeMixRewardedAdActivity.nativeAdAtomic.set(null)
            NativeMixRewardedAdActivity.callbackAtomic.set(null)
            NativeMixRewardedAdActivity.onUserEarnedRewardListenerAtomic.set(null)
        }
    }

    companion object {
        const val ARG_SCENE_NAME = "sceneName"
    }
}

class NativeMixInterstitialAdActivity : NativeMixFullscreenAdActivity() {

    companion object {

        val nativeAdAtomic: AtomicReference<NativeAdBase?> = AtomicReference(null)
        val callbackAtomic: AtomicReference<InnerFullScreenContentCallback?> = AtomicReference(null)

        internal fun start(
            context: Context,
            sceneName: String,
            ad: NativeAdBase,
            callback: InnerFullScreenContentCallback
        ) {
            nativeAdAtomic.set(ad)
            callbackAtomic.set(callback)
            val intent = Intent(context, NativeMixInterstitialAdActivity::class.java)
            intent.putExtra(ARG_SCENE_NAME, sceneName)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

class NativeMixOpenAdActivity : NativeMixFullscreenAdActivity() {

    companion object {

        val nativeAdAtomic: AtomicReference<NativeAdBase?> = AtomicReference(null)
        val callbackAtomic: AtomicReference<InnerFullScreenContentCallback?> = AtomicReference(null)

        internal fun start(
            context: Context,
            sceneName: String,
            ad: NativeAdBase,
            callback: InnerFullScreenContentCallback
        ) {
            nativeAdAtomic.set(ad)
            callbackAtomic.set(callback)
            val intent = Intent(context, NativeMixOpenAdActivity::class.java)
            intent.putExtra(ARG_SCENE_NAME, sceneName)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

class NativeMixRewardedAdActivity : NativeMixFullscreenAdActivity() {
    companion object {

        val nativeAdAtomic: AtomicReference<NativeAdBase?> = AtomicReference(null)
        val callbackAtomic: AtomicReference<InnerFullScreenContentCallback?> = AtomicReference(null)
        val onUserEarnedRewardListenerAtomic: AtomicReference<OnUserEarnedRewardListener?> =
            AtomicReference(null)

        internal fun start(
            context: Context,
            sceneName: String,
            ad: NativeAdBase,
            callback: InnerFullScreenContentCallback,
            onUserEarnedRewardListener: OnUserEarnedRewardListener
        ) {
            nativeAdAtomic.set(ad)
            callbackAtomic.set(callback)
            onUserEarnedRewardListenerAtomic.set(onUserEarnedRewardListener)
            val intent = Intent(context, NativeMixRewardedAdActivity::class.java)
            intent.putExtra(ARG_SCENE_NAME, sceneName)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
