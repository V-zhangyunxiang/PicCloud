package trade.ads.poseidon.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.poseidon.ad.api.PoseidonAPI
import com.poseidon.ad.main.processor.PlatformBannerAdLoadAndShowParameter
import com.poseidon.ad.restrict.gdpr.PlatformConsentFormShowParameter
import trade.ads.boost.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.showPrivacyForm.setOnClickListener {
            PoseidonAPI.doOnPrivacyOptionsIsRequired {
                PoseidonAPI.showPrivacyOptionsForm(
                    parameter = PlatformConsentFormShowParameter(
                        this
                    )
                )
            }
        }
        PoseidonAPI.loadAndShowBannerAdWithLifecycle(
            lifecycle = lifecycle,
            loadAndShowParameter = PlatformBannerAdLoadAndShowParameter(
                sceneName = "back_interstitial_scene",
                context = this,
                adViewContainer = binding.adViewContainer
            )
        )
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}