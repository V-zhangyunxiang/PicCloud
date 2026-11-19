package com.poseidon.ad.mediation.topon.init

import com.thinkup.core.api.TUSDK
import com.poseidon.ad.BuildConfig
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.core.processor.MediationSetupCallback
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.mediation.MEDIATION_TOPON
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import com.unity3d.ads.metadata.MetaData

object TopOnSetup: MediationSetup() {

    private const val TAG = "TopOnInitializer"

    override fun getMediationId(): Int = MEDIATION_TOPON

    override fun initMediation(callback: MediationSetupCallback) {
        val application = PoseidonSetupProcessor.Companion.getInstance().application

        MetaData(application).also { meta ->
            meta["user.nonbehavioral"] = false
            meta["privacy.useroveragelimit"] = true
            meta["privacy.consent"] = true
        }.commit()

        TUSDK.setNetworkLogDebug(BuildConfig.POSEIDON_DEBUG_SWITCH)
        if (BuildConfig.POSEIDON_DEBUG_SWITCH) {
            TUSDK.integrationChecking(application)
        }
        val topOn = PoseidonConfig.Companion.get()?.mediations?.topOn
        val appId = topOn?.appId
        val appKey = topOn?.appKey

        if (topOn != null && topOn.enablePresetStrategy && BuildConfig.POSEIDON_TOPON_STRATEGY_ENABLED) {
            // https://help.toponad.com/cn/docs/wERYJv?search=1
            // 仅在 QA/Release 中启用 TopOn 预置策略，避免 Debug 包误用正式投放策略。
            val dir = "localStrategy"
            TUSDK.setLocalStrategyAssetPath(application, dir)
            val children = application.assets.list(dir)
            if (children.isNullOrEmpty()) {
                if (BuildConfig.POSEIDON_DEBUG_SWITCH) {
                    error("[Crash only in DEBUG environment] TopOn preset strategy is enabled, but no strategy file is configured in the assets/${dir} directory. Please add the file.")
                } else {
                    ErrorDetectionReporter(
                        name = "TopOnPresetStrategyChecker",
                        extra = "TopOn preset strategy is enabled, but no strategy file is configured in the assets/${dir} directory."
                    ).analyze()
                }
            } else {
                val childSummary = children.joinToString(",")
                logInfo(TAG) {
                    "TopOn preset strategy is enabled! Files in the assets/$dir directory: $childSummary"
                }
            }
        } else {
            logInfo(TAG) {
                "TopOn preset strategy is not enabled for this build. enablePresetStrategy=${topOn?.enablePresetStrategy}, buildSwitch=${BuildConfig.POSEIDON_TOPON_STRATEGY_ENABLED}"
            }
        }

        if (appId.isNullOrEmpty()) {
            if (appKey.isNullOrEmpty()) {
                callback.onFailure(message = "TopOn appId and appKey is empty")
            } else {
                callback.onFailure(message = "TopOn appId is empty")
            }
        } else {
            if (appKey.isNullOrEmpty()) {
                callback.onFailure(message = "TopOn appKey is empty")
            } else {
                TUSDK.init(application, appId, appKey)
                callback.onSuccess()
            }
        }
    }
}
