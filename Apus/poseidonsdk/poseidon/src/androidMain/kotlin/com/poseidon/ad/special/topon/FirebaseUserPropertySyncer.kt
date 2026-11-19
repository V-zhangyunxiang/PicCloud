package com.poseidon.ad.special.topon

import android.os.Handler
import android.os.Looper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.statistics.SupportAnalyticsBackend

internal object FirebaseUserPropertySyncer {
    private const val TAG = "FirebaseUserProperty"
    private const val KEY_NETWORK = "poseidon_up_network"
    private const val KEY_CAMPAIGN = "poseidon_up_campaign"

    fun syncAtColdStart() {
        sync(TopOnAttributionSource.resolveForAdLoad())
    }

    fun sync(attribution: AttributionPayload) {
        val userProperties = attribution.toFirebaseUserProperties()
        if (userProperties == null) {
            logInfo(TAG) {
                "couldnt apply user property, channel=${attribution.channel}, campaign=${attribution.campaign}"
            }
            return
        }
        val setupProcessor = PoseidonSetupBaseProcessor.poseidonSetupProcessor()
        setupProcessor.analyticsFactory.obtain(
            by = SupportAnalyticsBackend.Firebase
        ).apply {
            setUserProperty(KEY_NETWORK, userProperties.network.userPropValue())
            setUserProperty(KEY_CAMPAIGN, userProperties.campaign.userPropValue())
        }
        Handler(Looper.getMainLooper()).postDelayed({
            setupProcessor.remoteConfig.callOnFetch()
        }, 3000)
    }
}

private fun String?.userPropValue(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.take(36)

internal data class FirebaseUserProperties(
    val network: String,
    val campaign: String,
)

internal fun AttributionPayload.toFirebaseUserProperties(): FirebaseUserProperties? {
    val channel = channel?.takeUnless(String::isEmpty) ?: return null
    val campaign = campaign?.takeUnless(String::isEmpty) ?: return null
    return FirebaseUserProperties(
        network = channel,
        campaign = campaign,
    )
}
