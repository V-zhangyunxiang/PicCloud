package com.poseidon.ad.mediation.admob.init

import com.google.android.gms.ads.MobileAds
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.core.processor.MediationSetupCallback
import com.poseidon.ad.mediation.MEDIATION_ADMOB

object AdmobSetup : MediationSetup() {
    private const val TAG = "AdmobInitializer"

    @Volatile
    private var _isInitComplete = false

    @Synchronized
    fun isInitComplete(): Boolean = _isInitComplete

    override fun getMediationId(): Int = MEDIATION_ADMOB

    override fun initMediation(callback: MediationSetupCallback) {
        val application = PoseidonSetupProcessor.Companion.getInstance().application
//        MobileAds.setRequestConfiguration(
//            RequestConfiguration.Builder()
//                .setTestDeviceIds(listOf(
//                    "D4849DA49B43FBFBE8639AEDBD49ED21"
//                ))
//                .build()
//        )
//        logInfo(TAG) {
//            "Test device set"
//        }
        MobileAds.initialize(application) { initializationStatus ->
            _isInitComplete = true
            val statusMap = initializationStatus.adapterStatusMap
            for (adapterClass in statusMap.keys) {
                val status = statusMap[adapterClass]
                logInfo(TAG) {
                    "MobileAds initialize completed, Adapter name: ${adapterClass}, Description: ${status?.description}, Latency: ${status?.latency}"
                }
            }
            callback.onSuccess()
        }
    }
}
