package com.poseidon.ad.restrict.traffic

import androidx.annotation.IntRange
import com.poseidon.ad.main.strategy.cryptor.Cryptor
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.common.toolkit.StringHelper

object TrafficLimit {

    @Suppress("SpellCheckingInspection")
    private val cryptor: Cryptor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Cryptor() }

    private fun getDeviceId(): String {
        val sharedPreference = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().sharedPreference
        val key = Key.DeviceIdGenerateBySdk
        var deviceId = sharedPreference.getString(key, null)
        if (deviceId.isNullOrEmpty()) {
            deviceId = StringHelper.newSessionId() + "_by_poseidon"
            sharedPreference.putString(key, deviceId)
        }
        return deviceId
    }

    private fun isPassable(@IntRange(from = 0, to = 100) scale: Int) : Boolean {
        return cryptor.isPassableByFlowControl(deviceId = getDeviceId(), scale = scale)
    }

    fun isPassable(remoteKey: String, @IntRange(from = 0, to = 100) defaultScale: Int) : Boolean {
        val key = "flow_control_scale_$remoteKey"
        val scale = PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().remoteConfig.getString(key).toIntOrNull() ?: defaultScale
        return isPassable(scale = scale)
    }
}