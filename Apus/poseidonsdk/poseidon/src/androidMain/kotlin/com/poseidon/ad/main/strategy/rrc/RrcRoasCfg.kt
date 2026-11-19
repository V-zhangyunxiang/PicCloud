package com.poseidon.ad.main.strategy.rrc

import android.os.Environment
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.cloud.FirebaseCloudException
import com.poseidon.ad.cloud.FirebaseCloudUpdateListener
import com.poseidon.ad.cloud.throwRemoteConfigurationFetchException
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.storage.sp.KeyProvider
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.main.strategy.common.isExternalStorageReadable
import com.poseidon.ad.main.strategy.cryptor.Cryptor
import com.poseidon.ad.restrict.traffic.TrafficLimit
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.jvm.JvmStatic

/**
 * RRC ROAS 云控配置，负责定义阈值与上报事件。
 */
@Serializable
internal data class RrcRoasCfg(
    @SerialName("pos_rrc_e") val switch: Int = 0,
    @SerialName("pos_rrc_ecpm_per") val ecpmPer: RrcRoasEcpmPer? = null,
    @SerialName("pos_rrc_ecpm_per2") val ecpmPerList: List<RrcRoasEcpmPer>? = null,
    @SerialName("pos_rrc_arpu_lim") val arpuLimits: List<RrcRoasArpuLimit>? = null,
) {

    /**
     * 是否开启 RRC ROAS 策略。
     */
    val enabled: Boolean
        get() = switch == 1

    /**
     * 合并所有 eCPM 阈值，避免重复配置。
     */
    val mergedEcpmList: List<RrcRoasEcpmPer> by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        val merged = mutableListOf<RrcRoasEcpmPer>()
        ecpmPerList?.let { merged.addAll(it) }
        ecpmPer?.let { single ->
            val exists = merged.any {
                it.ecpm == single.ecpm && it.adjustToken == single.adjustToken && it.firebaseEvent == single.firebaseEvent
            }
            if (!exists) {
                merged.add(single)
            }
        }
        merged
    }

    companion object : FirebaseCloudUpdateListener {
        private const val TAG = "RrcRoasCfg"
        private const val REMOTE_KEY = "poseidon_rrc_roas_config"

        @Volatile
        private var instance: RrcRoasCfg? = null
        private val lock = ReentrantLock()

        @Suppress("SpellCheckingInspection")
        private val cryptor: Cryptor by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { Cryptor() }

        @JvmStatic
        fun get(): RrcRoasCfg? {
            if (instance == null) {
                lock.withLock {
                    if (instance == null) {
                        instance = fromRemote()
                    }
                }
            }
            return instance
        }

        private fun fromRemote(): RrcRoasCfg? {
            val setupProcessor = poseidonSetupProcessor()
            return runCatching {
                fun remoteConfiguration(): String {
                    val encrypted = setupProcessor.remoteConfig.getString(REMOTE_KEY)
                    if (encrypted.isEmpty()) {
                        throwRemoteConfigurationFetchException("RRC-ROAS configuration is empty.")
                    }
                    return cryptor.decrypt(encrypted)
                }

                val json = if (setupProcessor.debug) {
                    getRrcRoasLocalConfiguration(fileName = "${REMOTE_KEY}.json").takeUnless { it.isNullOrEmpty() }
                        ?: remoteConfiguration()
                } else {
                    remoteConfiguration()
                }
                PoseidonJson.decodeFromString<RrcRoasCfg>(json)
            }.onFailure { throwable ->
                if (throwable is FirebaseCloudException) {
                    if (TrafficLimit.isPassable(
                            remoteKey = "RrcRoasConfigParseError_error_detection",
                            defaultScale = 30
                        )
                    ) {
                        ErrorDetectionReporter(
                            name = "RrcRoasConfigParseError",
                            extra = throwable.message.orEmpty()
                        ).analyze()
                    }
                } else {
                    ErrorDetectionReporter(
                        name = "RrcRoasConfigParseError",
                        extra = throwable.message.orEmpty()
                    ).analyze()
                }
            }.onSuccess { configuration ->
                logInfo(TAG) { "parse success: $configuration" }
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

/**
 * eCPM 阈值配置。
 */
@Serializable
internal data class RrcRoasEcpmPer(
    @SerialName("pos_ecpm") val ecpm: String,
    @SerialName("pos_adjust_token") val adjustToken: String? = "",
    @SerialName("pos_firebase_event") val firebaseEvent: String? = "",
) {
    val ecpmValue: Double?
        get() = ecpm.toDoubleOrNull()

    fun toKey(): KeyProvider = KeyProvider {
        Key.RrcRoasTracking.name + "_ecpm_" + (adjustToken.orEmpty()) + "_" + (firebaseEvent.orEmpty())
    }
}

/**
 * ARPU 限制配置。
 */
@Serializable
internal data class RrcRoasArpuLimit(
    @SerialName("pos_arpu") val arpu: String,
    @SerialName("pos_time_cycle") val timeCycle: String,
    @SerialName("pos_adjust_token") val adjustToken: String? = "",
    @SerialName("pos_firebase_event") val firebaseEvent: String? = "",
) {
    val arpuValue: Double?
        get() = arpu.toDoubleOrNull()
    val timeCycleHours: Int?
        get() = timeCycle.toIntOrNull()

    fun toKey(): KeyProvider = KeyProvider {
        Key.RrcRoasTracking.name + "_arpu_" + (adjustToken.orEmpty()) + "_" + (firebaseEvent.orEmpty())
    }
}

/**
 * 调试模式下读取本地配置。
 */
internal fun getRrcRoasLocalConfiguration(fileName: String): String? {
    return if (isExternalStorageReadable(PoseidonSetupProcessor.getInstance().application)) {
        File(Environment.getExternalStorageDirectory(), fileName)
            .takeIf { it.exists() && it.isFile }
            ?.readText()
    } else {
        null
    }
}
