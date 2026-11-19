package com.poseidon.ad.statistics.biz

import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

/**
 * CTR 限制打点。
 */
internal class CtrLimitReporter(
    private val forceClose: Boolean,
    private val resumedActivityNameWhenAdClicked: String?,
    private val resumedActivityNameWhenAdImpression: String?,
    private val waitForClosedActivityNames: String?,
    private val activityPartialNameFromRemoteConfig: String?,
) : Reporter(eventName = EventName.CTR_LIMIT) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.FORCE_CLOSE to forceClose,
            ParamKey.RESUMED_ACTIVITY_CLICK to resumedActivityNameWhenAdClicked,
            ParamKey.RESUMED_ACTIVITY_IMPRESSION to resumedActivityNameWhenAdImpression,
            ParamKey.WAIT_FOR_CLOSED_ACTIVITIES to waitForClosedActivityNames,
            ParamKey.ACTIVITY_PARTIAL_NAME_FROM_RC to activityPartialNameFromRemoteConfig,
        )
    }
}

/**
 * 关键购买行为打点。
 */
internal class KeyBehaviorBuyingAnalyzer(
    private val stage: Int,
    private val extra1: String?,
    private val extra2: String?,
    private val extra3: String?,
) : Reporter(eventName = EventName.KEY_BEHAVIOR) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.STAGE to stage,
            ParamKey.STAGE_EXTRA1 to extra1,
            ParamKey.STAGE_EXTRA2 to extra2,
            ParamKey.STAGE_EXTRA3 to extra3,
        )
    }
}

/**
 * 同意管理平台相关打点。
 */
internal class ConsentManagementPlatformAnalyzer private constructor(
    private val pair: Pair<String, Any>,
) : Reporter(eventName = EventName.CONSENT_PLATFORM) {

    override fun makeParameters(): Map<String, Any?> = mapOf(pair.first to pair.second)

    override fun defaultEnable(): Boolean = false

    companion object {
        private const val ENABLED_VALUE = 1
        private const val DISABLED_VALUE = -1000

        fun euCheck(isInEurope: Boolean) = ConsentManagementPlatformAnalyzer(
            pair = ParamKey.CMP_IS_IN_EUROPE to if (isInEurope) ENABLED_VALUE else DISABLED_VALUE
        )

        fun consentCheck(canRequestAds: Boolean) = ConsentManagementPlatformAnalyzer(
            pair = ParamKey.CMP_IS_CONSENT_AGREED to if (canRequestAds) ENABLED_VALUE else DISABLED_VALUE
        )

        fun showDialog(consentFormFromCache: Boolean) = ConsentManagementPlatformAnalyzer(
            pair = ParamKey.CMP_CONSENT_FORM_DIALOG_SHOW to if (consentFormFromCache) ENABLED_VALUE else DISABLED_VALUE
        )

        fun showPrivacyDialog() = ConsentManagementPlatformAnalyzer(
            pair = ParamKey.CMP_PRIVACY_OPTIONS_FORM_SHOW to ENABLED_VALUE
        )

        fun consentResult(agreeConsentForm: Boolean) = ConsentManagementPlatformAnalyzer(
            pair = ParamKey.CMP_CLICK_AGREE to if (agreeConsentForm) ENABLED_VALUE else DISABLED_VALUE
        )

        fun errorType(id: Int) = ConsentManagementPlatformAnalyzer(
            pair = ParamKey.CMP_ERROR_ID to id
        )

        fun errorMessage(errorMessage: String) = ConsentManagementPlatformAnalyzer(
            pair = ParamKey.CMP_ERROR_MSG to errorMessage
        )

        fun tagForUnderAgeOfConsent(tagForUnderAgeOfConsent: Boolean) = ConsentManagementPlatformAnalyzer(
            pair = ParamKey.CMP_UNDER_AGE to if (tagForUnderAgeOfConsent) ENABLED_VALUE else DISABLED_VALUE
        )
    }
}
