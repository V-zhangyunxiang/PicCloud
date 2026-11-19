package com.poseidon.ad.main.strategy.limit.show

import android.os.Environment
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.cloud.FirebaseCloudUpdateListener
import com.poseidon.ad.special.topon.AttributionChannelMtg
import com.poseidon.ad.main.strategy.cryptor.Cryptor
import com.poseidon.ad.restrict.traffic.TrafficLimit
import com.poseidon.ad.main.strategy.common.isExternalStorageReadable
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.cloud.FirebaseCloudException
import com.poseidon.ad.cloud.throwRemoteConfigurationFetchException
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.strategy.SWITCH_OFF
import java.io.File
import kotlin.jvm.JvmStatic

@Serializable
internal data class AdLimitShowCfg(
    @SerialName("pos_auto_clean_switch") val autoCleanSwitch: Int = -1,
    @SerialName("pos_n") val n: AdLimitShowItemCfg,
    @SerialName("pos_non_n") val nonN: AdLimitShowItemCfg,
) {

    fun isAutoCleanSwitchOn(): Boolean {
        return autoCleanSwitch != SWITCH_OFF
    }

    companion object : FirebaseCloudUpdateListener {
        private const val TAG = "AdLimitShowConfiguration"
        private const val REMOTE_KEY = "poseidon_ad_limit_show_config"

        @Volatile
        private var instance: AdLimitShowCfg? = null
        private val lock = ReentrantLock()

        @Suppress("SpellCheckingInspection")
        private val cryptor: Cryptor by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { Cryptor() }

        @JvmStatic
        fun get(): AdLimitShowCfg? {
            if (instance == null) {
                lock.withLock {
                    if (instance == null) {
                        instance = fromRemote()
                    }
                }
            }
            return instance
        }

        fun getAdLimitShowItem(): AdLimitShowItemCfg? {
            val config = get() ?: return null
            return if (AttributionChannelMtg.isEmptyOrOrganic()) {
                config.n
            } else {
                config.nonN
            }
        }

        private fun fromRemote(): AdLimitShowCfg? {
            val poseidonSetupProcessor = poseidonSetupProcessor()
            return runCatching {

                @Throws(exceptionClasses = [IllegalStateException::class])
                fun remoteConfiguration(): String {
                    val str = poseidonSetupProcessor.remoteConfig.getString(REMOTE_KEY)
                    val decryptedStr = str.ifEmpty {
                        throwRemoteConfigurationFetchException("The AdLimitShow configuration was not obtained from the Remote")
                    }
                    return cryptor.decrypt(decryptedStr)
                }

                val json = if (poseidonSetupProcessor.debug) {
                    getAdLimitShowLocalConfiguration(fileName = "${REMOTE_KEY}.json").takeUnless { it.isNullOrEmpty() }
                        ?: remoteConfiguration()
                } else {
                    remoteConfiguration()
                }

                PoseidonJson.decodeFromString<AdLimitShowCfg>(json)
            }.onFailure { t ->
                if (t is FirebaseCloudException) {
                    if (TrafficLimit.isPassable(remoteKey = "AdLimitShowConfigParseError_error_detection", defaultScale = 30)) {
                        ErrorDetectionReporter(
                            name = "AdLimitShowConfigParseError",
                            extra = t.message.orEmpty()
                        ).analyze()
                    }
                } else {
                    ErrorDetectionReporter(
                        name = "AdLimitShowConfigParseError",
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

@Serializable
internal data class AdLimitShowItemCfg(
    @SerialName("pos_switch") val switch: Int = -1,
    @SerialName("pos_scenes") val scenes: List<String>? = null,
    @SerialName("pos_tsf") val tsf: Boolean,
    @SerialName("pos_tif") val tif: Int,
    @SerialName("pos_til") val til: Int,
) {
    fun isSwitchOn(): Boolean {
        return switch != SWITCH_OFF
    }
}

fun getAdLimitShowLocalConfiguration(fileName: String): String? {
    return if (isExternalStorageReadable(PoseidonSetupProcessor.getInstance().application)) {
        File(Environment.getExternalStorageDirectory(), fileName)
            .takeIf { it.exists() && it.isFile }?.readText()
    } else {
        null
    }
}
