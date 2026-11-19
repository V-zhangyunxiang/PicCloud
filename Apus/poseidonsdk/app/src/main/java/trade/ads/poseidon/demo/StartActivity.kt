package trade.ads.poseidon.demo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.api.RewardedShowListener
import com.poseidon.ad.api.OnUserEarnedRewardListener
import com.poseidon.ad.api.RewardItem
import trade.ads.boost.app.databinding.ActivityStartBinding
import com.poseidon.ad.main.processor.PoseidonAPIProcessor
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.main.processor.PlatformNativeAdShowParameter
import com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter
import com.poseidon.ad.main.processor.PlatformRewardedAdShowParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.poseidon.ad.api.PoseidonAPI
import com.poseidon.ad.common.toolkit.Cancelable
import java.util.TimerTask

class StartActivity : AppCompatActivity(){

    fun mainScope(): CoroutineScope = CoroutineScope(Dispatchers.Main)

    private var multiplatformInterstitialAdCancelable: Cancelable? = null
    private var multiplatformNativeAdCancelable: Cancelable? = null
    private var multiplatformRewardedAdCancelable: Cancelable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        val binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mainScope().launch {
            PoseidonAPI.loadInterstitialAd(
                PlatformInterstitialAdLoadParameter(
                    context = this@StartActivity,
                    sceneName = "HBL_Newuser_inter"
                )
            )
        }

        binding.root.setBackgroundColor(Color.RED)
        binding.remove.setOnClickListener {
            binding.nativeAdView.removeAllViews()

            multiplatformInterstitialAdCancelable?.cancel()
            multiplatformInterstitialAdCancelable = null

            multiplatformNativeAdCancelable?.cancel()
            multiplatformNativeAdCancelable = null

            multiplatformRewardedAdCancelable?.cancel()
            multiplatformRewardedAdCancelable = null
        }

        binding.toMain.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        binding.load.setOnClickListener {
            mainScope().launch {
                PoseidonAPIProcessor.loadRewardedAd(PlatformRewardedAdLoadParameter(
                    context = this@StartActivity,
                    sceneName = "HBL_Level_REW"
                ))
            }
        }

        binding.start.setOnClickListener {
            multiplatformNativeAdCancelable = PoseidonAPIProcessor.showNativeAd(
                PlatformNativeAdShowParameter(
                    activity = this,
                    sceneName = "HBL_Level_REW",
                    templateView = binding.nativeAdView,
                    adShowListener = object : AdShowListener {
                        override fun onAdClicked() {
                            Log.i("StartActivity", "onAdClicked: ")
                        }

                        override fun onAdClosed() {
                            Log.i("StartActivity", "onAdClosed: ")
                        }

                        override fun onAdShowFailed(code: Int, message: String) {
                            Log.i("StartActivity", "onAdFailedToShow: code=$code, message=$message")
                        }

                        override fun onAdShowSuccess() {
                            Log.i("StartActivity", "onAdImpression: ")
                        }
                    }
                )
            )
            multiplatformRewardedAdCancelable = PoseidonAPIProcessor.showRewardedAd(
                showParameter = PlatformRewardedAdShowParameter(
                    activity = this,
                    sceneName = "back_interstitial_scene",
                    onUserEarnedRewardListener = object : OnUserEarnedRewardListener {
                        override fun onUserEarnedReward(reward: RewardItem) {
                            Log.i("StartActivity", "onUserEarnedReward: reward=$reward")
                        }
                    },
                    rewardedShowListener = object : RewardedShowListener {
                        override fun onAdClicked() {
                            Log.i("StartActivity", "onAdClicked: ")
                        }

                        override fun onAdDismissedFullScreenContent() {
                            Log.i("StartActivity", "onAdDismissedFullScreenContent: ")
                        }

                        override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
                            Log.i("StartActivity", "onAdFailedToShowFullScreenContent: code=$code, message=$message")
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.i("StartActivity", "onAdShowedFullScreenContent: ")
                        }
                    }
                )
            )
        }
    }
}

private fun TimerTask.autoDisposed(owner: LifecycleOwner) {
    owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            owner.lifecycle.removeObserver(this)
            cancel()
        }
    })
}
