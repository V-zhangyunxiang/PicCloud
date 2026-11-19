package com.poseidon.ad.main.strategy.limit.load.mode

import com.poseidon.ad.cloud.FirebaseCloudException
import com.poseidon.ad.cloud.FirebaseCloudUpdateListener
import com.poseidon.ad.cloud.throwRemoteConfigurationFetchException
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.main.load.AdLoadTrafficControlMode
import com.poseidon.ad.main.load.getAdLoadModeControlLocalConfiguration
import com.poseidon.ad.main.strategy.cryptor.Cryptor
import com.poseidon.ad.restrict.traffic.TrafficLimit
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AdLoadModeControlCfg(
    @SerialName("pos_mode_default") val modeDefault: String,
    @SerialName("pos_mode_serial") val modeSerial: ModeSerialCfg?,
    @SerialName("pos_mode_delay") val modeDelay: ModeDelayCfg?,
) {

    fun defaultMode(): AdLoadTrafficControlMode {
        return AdLoadTrafficControlMode.entries.find { it.value == modeDefault }
            ?: AdLoadTrafficControlMode.IMMEDIATE
    }

    companion object : FirebaseCloudUpdateListener {
        private const val TAG = "AdLoadTrafficControlConfiguration"
        private const val REMOTE_KEY = "poseidon_ad_load_mode_control_config"

        @Volatile
        private var instance: AdLoadModeControlCfg? = null
        private val lock = ReentrantLock()

        @Suppress("SpellCheckingInspection")
        private val cryptor: Cryptor by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { Cryptor() }

        @JvmStatic
        fun get(): AdLoadModeControlCfg? {
            if (instance == null) {
                lock.withLock {
                    if (instance == null) {
                        instance = fromRemote()
                    }
                }
            }
            return instance
        }

        private fun fromRemote(): AdLoadModeControlCfg? {
            val poseidonSetupProcessor = poseidonSetupProcessor()
            return runCatching {

                @Throws(exceptionClasses = [IllegalStateException::class])
                fun remoteConfiguration(): String {
                    val str = poseidonSetupProcessor.remoteConfig.getString(REMOTE_KEY)
                    val decryptedStr = str.ifEmpty {
                        throwRemoteConfigurationFetchException("The AdLoadModeControl configuration was not obtained from the Remote")
                    }
                    return cryptor.decrypt(decryptedStr)
                }

                val json = if (poseidonSetupProcessor.debug) {
                    getAdLoadModeControlLocalConfiguration(fileName = "${REMOTE_KEY}.json").takeUnless { it.isNullOrEmpty() }
                        ?: remoteConfiguration()
                } else {
                    remoteConfiguration()
                }

                PoseidonJson.decodeFromString<AdLoadModeControlCfg>(json)
            }.onFailure { t ->
                if (t is FirebaseCloudException) {
                    if (TrafficLimit.isPassable(
                            remoteKey = "AdLoadTrafficControlConfigParseError_error_detection",
                            defaultScale = 30
                        )
                    ) {
                        ErrorDetectionReporter(
                            name = "AdLoadTrafficControlConfigParseError",
                            extra = t.message.orEmpty()
                        ).analyze()
                    }
                } else {
                    ErrorDetectionReporter(
                        name = "AdLoadTrafficControlConfigParseError",
                        extra = t.message.orEmpty()
                    ).analyze()
                }
            }.onSuccess { configuration ->
                logInfo(TAG) {
                    "fromRemote.onSuccess: $configuration"
                }
            }.getOrNull()
        }

        override fun onUpdate(updatedKeys: Set<String>) {
            if (updatedKeys.contains(REMOTE_KEY)) {
                lock.withLock {
                    instance = fromRemote()
                }
            }
        }
    }
}
