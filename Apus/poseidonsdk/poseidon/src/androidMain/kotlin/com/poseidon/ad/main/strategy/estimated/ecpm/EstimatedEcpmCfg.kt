package com.poseidon.ad.main.strategy.estimated.ecpm

import android.os.Environment
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.cloud.FirebaseCloudException
import com.poseidon.ad.cloud.FirebaseCloudUpdateListener
import com.poseidon.ad.cloud.throwRemoteConfigurationFetchException
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.main.adinfo.AdSourceInfo
import com.poseidon.ad.main.strategy.common.isExternalStorageReadable
import com.poseidon.ad.main.strategy.cryptor.Cryptor
import com.poseidon.ad.restrict.traffic.TrafficLimit
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Suppress("SpellCheckingInspection")
@Serializable
internal data class EstimatedEcpmConfigResponse(
    @SerialName("pos_ecpm_version") val versionString: String? = null,
    @SerialName("pos_ecpm_configs") val configs: List<EstimatedEcpmCfg>
) {
    val version: VersionHelper? by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        versionString?.let(VersionHelper::tryFrom)
    }
}

/**
 * Represents an estimated eCPM configuration
 */
@Suppress("SpellCheckingInspection")
@Serializable
internal data class EstimatedEcpmCfg(
    @SerialName("pos_ad_unit_id") val adUnitId: String,
    @SerialName("pos_estimated_ecpm") val estimatedEcpm: Double,
    @SerialName("pos_network") val network: Int,
    @SerialName("pos_ad_type") val adType: Int,
    @SerialName("pos_ad_network_configs") val adNetworkConfigs: List<EstimatedEcpmAdNetworkCfg>? = null,
) {
    fun resolveEstimatedEcpm(adSourceInfo: AdSourceInfo?): Double {
        val sourceLevelEstimatedEcpm = adSourceInfo?.let { info ->
            adNetworkConfigs
                ?.firstOrNull { networkCfg ->
                    networkCfg.networkUnitId == info.adSourcePlacementId &&
                        networkCfg.adSourceId == info.adSourceId
                }
                ?.estimatedEcpm
        }
        return sourceLevelEstimatedEcpm ?: estimatedEcpm
    }

    companion object : FirebaseCloudUpdateListener {
        private const val TAG = "EstimatedEcpmConfiguration"
        private const val REMOTE_KEY = "poseidon_estimated_ecpm_config_new"

        @Volatile
        private var instance: EstimatedEcpmConfigResponse? = null
        private val lock = ReentrantLock()

        @Suppress("SpellCheckingInspection")
        private val cryptor: Cryptor by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { Cryptor() }

        private fun ensureConfigIsLoaded(): EstimatedEcpmConfigResponse? {
            if (instance == null) {
                lock.withLock {
                    if (instance == null) {
                        instance = fromRemote()
                    }
                }
            }
            return instance
        }

        @JvmStatic
        fun get(): List<EstimatedEcpmCfg>? {
            return ensureConfigIsLoaded()?.configs
        }

        @JvmStatic
        fun version(): VersionHelper? = instance?.version

        fun prewarm() {
            get()
        }

        private fun fromRemote(): EstimatedEcpmConfigResponse? {
            val poseidonSetupProcessor = poseidonSetupProcessor()
            return runCatching {
                fun remoteConfiguration(): EstimatedEcpmConfigResponse? {
                    val str = poseidonSetupProcessor.remoteConfig.getString(REMOTE_KEY)
                    if (str.isEmpty()) return null
                    return parseEstimatedEcpmConfigurationJson(cryptor.decrypt(str))
                }

                fun localConfiguration(): EstimatedEcpmConfigResponse? = runCatching {
                    getEstimatedEcpmLocalConfiguration(fileName = "${REMOTE_KEY}.json")
                        ?.takeUnless(String::isEmpty)
                        ?.let(::parseEstimatedEcpmConfigurationJson)
                }.getOrNull()

                val resolved = if (poseidonSetupProcessor.debug) {
                    localConfiguration() ?: remoteConfiguration()
                } else {
                    remoteConfiguration()
                }
                resolved ?: throwRemoteConfigurationFetchException("The EstimatedEcpm configuration was not resolved.")
            }.onFailure { t ->
                if (t is FirebaseCloudException) {
                    if (TrafficLimit.isPassable(remoteKey = "EstimatedEcpmConfigParseError_error_detection", defaultScale = 30)) {
                        ErrorDetectionReporter(
                            name = "EstimatedEcpmConfigParseError",
                            extra = t.message.orEmpty()
                        ).analyze()
                    }
                } else {
                    ErrorDetectionReporter(
                        name = "EstimatedEcpmConfigParseError",
                        extra = t.message.orEmpty()
                    ).analyze()
                }
            }.onSuccess { configuration ->
                logInfo(TAG) {
                    "fromRemote.onSuccess: version=${configuration.version}, configs=${configuration.configs}"
                }
            }.getOrNull()
        }

        override fun onUpdate(updatedKeys: Set<String>) {
            if (!updatedKeys.contains(REMOTE_KEY)) return
            lock.withLock {
                val old = instance
                val new = fromRemote() ?: return
                val oldVersion = old?.version
                val newVersion = new.version
                val shouldReplace = when {
                    old == null -> true
                    oldVersion == null && newVersion == null -> true
                    oldVersion == null -> true
                    newVersion == null -> false
                    else -> newVersion > oldVersion
                }
                if (shouldReplace) {
                    instance = new
                    logInfo(TAG) {
                        "onUpdate: new version=$newVersion, oldVersion=$oldVersion"
                    }
                }
            }
        }
    }
}

@Suppress("SpellCheckingInspection")
@Serializable
internal data class EstimatedEcpmAdNetworkCfg(
    @SerialName("pos_network_unit_id") val networkUnitId: String,
    @SerialName("pos_ad_source_id") val adSourceId: Int,
    @SerialName("pos_estimated_ecpm") val estimatedEcpm: Double,
)

internal fun parseEstimatedEcpmConfigurationJson(json: String): EstimatedEcpmConfigResponse {
    return runCatching {
        PoseidonJson.decodeFromString<EstimatedEcpmConfigResponse>(json)
    }.getOrElse {
        EstimatedEcpmConfigResponse(
            configs = PoseidonJson.decodeFromString<List<EstimatedEcpmCfg>>(json)
        )
    }
}

@Suppress("SpellCheckingInspection")
internal fun getEstimatedEcpmLocalConfiguration(fileName: String): String? {
    return if (isExternalStorageReadable(PoseidonSetupProcessor.getInstance().application)) {
        File(Environment.getExternalStorageDirectory(), fileName)
            .takeIf { it.exists() && it.isFile }?.readText()
    } else {
        null
    }
}
