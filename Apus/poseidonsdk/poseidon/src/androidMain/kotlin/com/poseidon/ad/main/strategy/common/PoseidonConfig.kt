package com.poseidon.ad.main.strategy.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.poseidon.ad.BuildConfig
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.cloud.FirebaseCloudUpdateListener
import com.poseidon.ad.cloud.throwRemoteConfigurationFetchException
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.main.strategy.SWITCH_OFF
import com.poseidon.ad.main.strategy.cryptor.Cryptor
import com.poseidon.ad.special.topon.AttributionPayload
import com.poseidon.ad.special.topon.PoseidonKaAttributionMatcher
import com.poseidon.ad.special.topon.extractCampaignIdentifier
import com.poseidon.ad.statistics.base.AdConfigVersionReporter
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import com.poseidon.ad.statistics.base.FirebaseCloudReporter
import com.poseidon.ad.statistics.base.KaConfigBootstrapDecisionReporter
import com.poseidon.ad.statistics.biz.KeyBehaviorBuyingAnalyzer
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Represents a configuration
 * @param versionString the version of this configuration, format must be <major>.<minor>.<patch>
 * @param switch global switch, [SWITCH_OFF] for disabling, others for enabling
 * @param strategies ad strategy list
 * @param scenes scene list
 * @param notificationSplashAd notification splash ad configuration
 * @param sharedFloor shared floor price
 * @param sharedCeil shared ceil price
 * @param globalControl global frequency control configuration
 * @param adTypeControls ad type frequency control configuration
 * @param mediations ad network configuration
 */
@Serializable
internal data class PoseidonConfig(
    @SerialName("pos_version") val versionString: String,
    @SerialName("pos_switch") val switch: Int = -1,
    @SerialName("pos_strategy") val strategies: List<StrategyCfg>?,
    @SerialName("pos_scene") val scenes: List<SceneCfg>?,
    @SerialName("pos_noti_splash_ad") val notificationSplashAd: NotificationSplashAdCfg? = null,
    @SerialName("pos_shared_floor") val sharedFloor: SharedCfg? = null,
    @SerialName("pos_shared_ceiling") val sharedCeil: SharedCfg? = null,
    @SerialName("pos_specific_scene_cache") val specificAdSceneCacheBackupConfiguration: SpecificSceneCfg? = null,
    @SerialName("pos_automation") val automation: AutomationCfg? = null,
    @SerialName("pos_global_control") val globalControl: GlobalFrequencyControlCfg? = null,
    @SerialName("pos_ad_type_controls") val adTypeControls: AdTypeFrequencyControlsCfg? = null,
    @SerialName("pos_mediations") val mediations: MediationsCfg?,
    @SerialName("pos_adjust_tokens") val adjustTokens: AdjustEventTokensCfg? = null,
) {

    val version: VersionHelper? by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        VersionHelper.tryFrom(versionString = versionString)
    }

    fun isSwitchOn(): Boolean {
        return switch != SWITCH_OFF
    }

    companion object : FirebaseCloudUpdateListener {
        private const val TAG = "Configuration"
        private const val REMOTE_KEY = "poseidon_config"
        private const val KEY_BEHAVIOR_BUYING_PREFIX = "poseidon_ka_${REMOTE_KEY}_"
        private const val KA_WIN_SOURCE_ON_UPDATE = "on_update"
        private const val KA_WIN_SOURCE_ATTRIBUTION_CHANGED = "attribution_changed"

        @Volatile
        private var instance: PoseidonConfig? = null
        private val lock = ReentrantLock()

        @Suppress("SpellCheckingInspection")
        private val cryptor: Cryptor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Cryptor() }

        @JvmStatic
        fun version(): VersionHelper? {
            return instance?.version
        }

        @JvmStatic
        fun get(): PoseidonConfig? {
            if (instance == null) {
                lock.withLock {
                    if (instance == null) {
                        instance = resolveBootstrapConfiguration()
                        AdConfigVersionReporter(
                            from = "Default",
                            version = instance?.version?.toString().orEmpty()
                        ).analyze()
                    }
                }
            }
            return instance
        }

        fun prewarm() {
            get()
        }

        private fun resolveBootstrapConfiguration(): PoseidonConfig? {
            val defaultConfig = fromRemote()
            val decision = evaluateBootstrapDecision(defaultConfig)
            KaConfigBootstrapDecisionReporter(
                defaultVersion = decision.defaultVersion?.toString(),
                kaVersion = decision.kaVersion?.toString(),
                kaKey = decision.kaKey,
                winner = decision.winner.value,
                reason = decision.reason.value,
                kaSource = decision.kaSource
            ).analyze()
            return decision.configuration
        }

        private fun parseEncryptedConfiguration(
            encryptedConfigString: String,
            sourceLabel: String,
        ): PoseidonConfig? {
            if (encryptedConfigString.isEmpty()) {
                logInfo(TAG) { "$sourceLabel string is empty" }
                return null
            }
            logInfo(TAG) { "$sourceLabel string found, size: ${encryptedConfigString.length} chars" }
            val json = cryptor.decrypt(encryptedConfigString)
            if (json.isEmpty()) {
                logWarn(tag = TAG) { "$sourceLabel decryption resulted in empty string" }
                return null
            }
            logInfo(TAG) { "$sourceLabel decrypted successfully, size: ${json.length} chars" }
            val config = PoseidonJson.decodeFromString<PoseidonConfig>(json)
            logInfo(TAG) { "$sourceLabel parsed successfully: version=${config.version}, switch=${config.switch}" }
            return config
        }

        private fun fromRemote(remoteKey: String = REMOTE_KEY): PoseidonConfig? {
            val poseidonSetupProcessor = poseidonSetupProcessor()
            return runCatching {

                fun resolveFromRemoteConfig(): PoseidonConfig? = runCatching {
                    logInfo(TAG) { "Starting remote configuration read from Firebase" }
                    val str = poseidonSetupProcessor.remoteConfig.getString(remoteKey)
                    parseEncryptedConfiguration(
                        encryptedConfigString = str,
                        sourceLabel = "Remote configuration",
                    )
                }.onFailure { t ->
                    logWarn(throwable = t, tag = TAG) {
                        "Failed to read remote configuration: ${t.message}"
                    }
                }.getOrNull()

                // 解析内置 release 配置，替代历史 poseidon.config 方案
                fun resolveFromEmbeddedRelease(): PoseidonConfig? = runCatching {
                    logInfo(TAG) { "Starting embedded release configuration read" }
                    val str = getEmbeddedReleaseConfiguration(TAG)
                    parseEncryptedConfiguration(
                        encryptedConfigString = str.orEmpty(),
                        sourceLabel = "Embedded release configuration",
                    )
                }.onFailure { t ->
                    logWarn(throwable = t, tag = TAG) {
                        "Failed to read embedded release configuration: ${t.message}"
                    }
                }.getOrNull()

                fun resolveFromRelease(): PoseidonConfig? {
                    logInfo(TAG) { "Starting Release mode configuration resolution" }
                    val embedded = resolveFromEmbeddedRelease()
                    val remote = resolveFromRemoteConfig()

                    logInfo(TAG) { "Configuration sources - Embedded: ${embedded != null}, Remote: ${remote != null}" }

                    return if (embedded == null) {
                        logInfo(TAG) { "No embedded configuration, using remote configuration" }
                        remote
                    } else {
                        if (remote == null) {
                            logInfo(TAG) { "No remote configuration, using embedded configuration" }
                            embedded
                        } else {
                            val embeddedVersion = embedded.version
                            val remoteVersion = remote.version
                            logInfo(TAG) { "Version comparison - Embedded: $embeddedVersion, Remote: $remoteVersion" }

                            if (embeddedVersion == null) {
                                if (remoteVersion == null) {
                                    logInfo(TAG) { "Both versions are null, using embedded configuration" }
                                    embedded
                                } else {
                                    logInfo(TAG) { "Embedded version is null but remote has version, using remote configuration" }
                                    remote
                                }
                            } else {
                                if (remoteVersion == null) {
                                    logInfo(TAG) { "Remote version is null but embedded has version, using embedded configuration" }
                                    embedded
                                } else {
                                    if (remoteVersion > embeddedVersion) {
                                        logInfo(TAG) { "Remote version ($remoteVersion) > Embedded version ($embeddedVersion), using remote configuration" }
                                        remote
                                    } else {
                                        logInfo(TAG) { "Embedded version ($embeddedVersion) >= Remote version ($remoteVersion), using embedded configuration" }
                                        embedded
                                    }
                                }
                            }
                        }
                    }
                }

                fun resolveFromLocal(): PoseidonConfig? = runCatching {
                    logInfo(TAG) { "Starting local configuration read from external storage" }
                    val json = getLocalConfiguration(fileName = "${REMOTE_KEY}.json")
                    if (json.isNullOrEmpty()) {
                        logInfo(TAG) { "Local configuration file not found or empty" }
                        null
                    } else {
                        logInfo(TAG) { "Local configuration file found, size: ${json.length} chars" }
                        val config = PoseidonJson.decodeFromString<PoseidonConfig>(json)
                        logInfo(TAG) { "Local configuration parsed successfully: $config" }
                        config
                    }
                }.onFailure { t ->
                    logWarn(throwable = t, tag = TAG) {
                        "Failed to read local configuration: ${t.message}"
                    }
                }.getOrNull()

                if (poseidonSetupProcessor.debug) {
                    logInfo(TAG) { "Debug mode: trying local configuration first" }
                    val localConfig = resolveFromLocal()
                    if (localConfig != null) {
                        logInfo(TAG) { "Debug mode: using local configuration successfully" }
                        localConfig
                    } else {
                        logInfo(TAG) { "Debug mode: local configuration not available, falling back to release mode" }
                        resolveFromRelease()
                    }
                } else {
                    logInfo(TAG) { "Release mode: using release configuration resolution" }
                    resolveFromRelease()
                }
                    ?: throwRemoteConfigurationFetchException("The configuration was not obtained from Firebase Remote Config nor from the embedded release file.")
            }.onFailure { t ->
                if (poseidonSetupProcessor.debug) {
                    error(t)
                } else {
                    ErrorDetectionReporter(
                        name = "poseidonConfigParseError",
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
            val setupProcessor = poseidonSetupProcessor()
            val effectiveAttribution = PoseidonKaAttributionMatcher.resolveEffectiveAttribution()
            val remoteKeys = keyBehaviorBuyingRemoteKeys(effectiveAttribution)
            val keyBehaviorBuyingKey = remoteKeys.firstOrNull { key ->
                setupProcessor.remoteConfig.getString(key).isNotEmpty()
            }
            KeyBehaviorBuyingAnalyzer(
                stage = 1,
                extra1 = remoteKeys.joinToString(","),
                extra2 = KEY_BEHAVIOR_BUYING_PREFIX,
                extra3 = keyBehaviorBuyingKey,
            ).analyze()
            if (keyBehaviorBuyingKey != null) {
                val keySuffix =
                    keyBehaviorBuyingKey.removePrefix(KEY_BEHAVIOR_BUYING_PREFIX)
                val effectiveAttribution =
                    PoseidonKaAttributionMatcher.resolveEffectiveAttribution()
                KeyBehaviorBuyingAnalyzer(
                    stage = 2,
                    extra1 = keyBehaviorBuyingKey,
                    extra2 = keySuffix,
                    extra3 = PoseidonKaAttributionMatcher.describe(effectiveAttribution),
                ).analyze()
                if (PoseidonKaAttributionMatcher.matchesKeySuffix(
                        keySuffix,
                        effectiveAttribution
                    )
                ) {
                    lock.withLock {
                        val encryptedConfig =
                            setupProcessor.remoteConfig.getString(keyBehaviorBuyingKey)
                        val new = runCatching {
                            parseEncryptedConfiguration(
                                encryptedConfigString = encryptedConfig,
                                sourceLabel = "KA remote configuration[$keyBehaviorBuyingKey]",
                            )
                        }.onFailure { throwable ->
                            logWarn(throwable = throwable, tag = TAG) {
                                "Failed to read KA remote configuration: ${throwable.message}"
                            }
                        }.getOrNull()
                        if (new != null) {
                            KeyBehaviorBuyingAnalyzer(
                                stage = 3,
                                extra1 = new.versionString,
                                extra2 = keySuffix,
                                extra3 = PoseidonKaAttributionMatcher.describe(effectiveAttribution),
                            ).analyze()
                        }
                        val newVersion = new?.version
                        if (newVersion != null) {
                            FirebaseCloudReporter(
                                versionName = newVersion.toString()
                            ).analyze()
                            val old = instance
                            val oldVersion = old?.version
                            if (old == null || oldVersion == null || oldVersion < newVersion) {
                                instance = new
                                KaConfigBootstrapDecisionReporter(
                                    defaultVersion = oldVersion?.toString(),
                                    kaVersion = newVersion.toString(),
                                    kaKey = keyBehaviorBuyingKey,
                                    winner = PoseidonKaBootstrapWinner.KA.value,
                                    reason = PoseidonKaBootstrapReason.KA_HIGHER.value,
                                    kaSource = KA_WIN_SOURCE_ON_UPDATE
                                ).analyze()
                                AdConfigVersionReporter(
                                    from = "Update",
                                    version = newVersion.toString()
                                ).analyze()
                            }
                        }
                    }
                }
                return
            }

            if (updatedKeys.contains(REMOTE_KEY)) {
                lock.withLock {
                    val new = fromRemote()
                    val newVersion = new?.version
                    if (new != null && newVersion != null) {
                        FirebaseCloudReporter(
                            versionName = newVersion.toString()
                        ).analyze()
                        val old = instance
                        val oldVersion = old?.version
                        if (old == null || oldVersion == null || oldVersion < newVersion) {
                            instance = new
                            AdConfigVersionReporter(
                                from = "Update",
                                version = newVersion.toString()
                            ).analyze()
                        }
                    }
                }
            }
        }

        @Suppress("SpellCheckingInspection")
        fun updateFromBackend(encryptedPoseidonConfigString: String) {
            val poseidonSetupProcessor = poseidonSetupProcessor()
            logInfo(TAG) { "Starting backend configuration update, encrypted string size: ${encryptedPoseidonConfigString.length} chars" }
            val new = runCatching {
                val json = cryptor.decrypt(encryptedPoseidonConfigString)
                logInfo(TAG) { "Backend configuration decrypted successfully, size: ${json.length} chars" }
                PoseidonJson.decodeFromString<PoseidonConfig>(json)
            }.onFailure { t ->
                logWarn(throwable = t, tag = TAG) {
                    "Failed to decrypt/parse backend configuration: ${t.message}"
                }
                if (poseidonSetupProcessor.debug) {
                    error(t)
                } else {
                    ErrorDetectionReporter(
                        name = "poseidonConfigFromBackendParseError",
                        extra = t.message.orEmpty()
                    ).analyze()
                }
            }.onSuccess { configuration ->
                logInfo(TAG) {
                    "updateFromBackend.onSuccess: $configuration"
                }
            }.getOrNull()

            lock.withLock {
                val newVersion = new?.version
                if (new != null && newVersion != null) {
                    val old = instance
                    val oldVersion = old?.version
                    if (old == null || oldVersion == null || oldVersion < newVersion) {
                        instance = new
                        AdConfigVersionReporter(
                            from = "Backend",
                            version = newVersion.toString()
                        ).analyze()
                    }
                }
            }
        }

        internal fun reEvaluateKaWhenAttributionChanged() {
            lock.withLock {
                val old = instance
                val oldVersion = old?.version
                val kaCandidateState = runCatching {
                    readKaCandidateFromRemoteConfigCache()
                }.onFailure { t ->
                    logWarn(throwable = t, tag = TAG) {
                        "Failed to re-evaluate KA candidate when attribution changed: ${t.message}"
                    }
                }.getOrNull() ?: return
                val validCandidate =
                    (kaCandidateState as? PoseidonKaCandidateState.Valid)?.candidate
                        ?: return
                val new = validCandidate.configuration
                val newVersion = new.version
                if (newVersion != null && (old == null || oldVersion == null || oldVersion < newVersion)
                ) {
                    instance = new
                    KaConfigBootstrapDecisionReporter(
                        defaultVersion = oldVersion?.toString(),
                        kaVersion = newVersion.toString(),
                        kaKey = validCandidate.remoteKey,
                        winner = PoseidonKaBootstrapWinner.KA.value,
                        reason = PoseidonKaBootstrapReason.KA_HIGHER.value,
                        kaSource = KA_WIN_SOURCE_ATTRIBUTION_CHANGED
                    ).analyze()
                    AdConfigVersionReporter(
                        from = "AttributionChanged",
                        version = newVersion.toString()
                    ).analyze()
                }
            }
        }

        private fun evaluateBootstrapDecision(defaultConfig: PoseidonConfig?): PoseidonKaBootstrapDecision {
            return selectPoseidonKaBootstrapConfiguration(
                defaultConfig = defaultConfig,
                kaCandidateState = readKaCandidateFromRemoteConfigCache(),
            )
        }


        private fun readKaCandidateFromRemoteConfigCache(): PoseidonKaCandidateState {
            val effectiveAttribution = PoseidonKaAttributionMatcher.resolveEffectiveAttribution()
            val remoteKeys = keyBehaviorBuyingRemoteKeys(effectiveAttribution)
            val setupProcessor = poseidonSetupProcessor()
            val keyBehaviorBuyingKey = remoteKeys.firstOrNull { key ->
                setupProcessor.remoteConfig.getString(key).isNotEmpty()
            }
            KeyBehaviorBuyingAnalyzer(
                stage = 1,
                extra1 = remoteKeys.joinToString(","),
                extra2 = KEY_BEHAVIOR_BUYING_PREFIX,
                extra3 = keyBehaviorBuyingKey,
            ).analyze()
            if (keyBehaviorBuyingKey == null) {
                return PoseidonKaCandidateState.Missing
            }

            val keySuffix =
                keyBehaviorBuyingKey.removePrefix(KEY_BEHAVIOR_BUYING_PREFIX)
            KeyBehaviorBuyingAnalyzer(
                stage = 2,
                extra1 = keyBehaviorBuyingKey,
                extra2 = keySuffix,
                extra3 = PoseidonKaAttributionMatcher.describe(effectiveAttribution),
            ).analyze()
            if (PoseidonKaAttributionMatcher.matchesKeySuffix(keySuffix, effectiveAttribution).not()
            ) {
                return PoseidonKaCandidateState.Missing
            }
            val encryptedConfig = setupProcessor.remoteConfig.getString(keyBehaviorBuyingKey)
            if (encryptedConfig.isEmpty()) {
                return PoseidonKaCandidateState.Invalid(
                    remoteKey = keyBehaviorBuyingKey
                )
            }
            return runCatching {
                val json = cryptor.decrypt(encryptedConfig)
                val config = PoseidonJson.decodeFromString<PoseidonConfig>(json)
                val newVersion = config.version ?: return PoseidonKaCandidateState.Invalid(
                    storedVersion = config.versionString,
                    remoteKey = keyBehaviorBuyingKey
                )
                KeyBehaviorBuyingAnalyzer(
                    stage = 3,
                    extra1 = config.versionString,
                    extra2 = keySuffix,
                    extra3 = PoseidonKaAttributionMatcher.describe(effectiveAttribution),
                ).analyze()
                PoseidonKaCandidateState.Valid(
                    candidate = ValidPoseidonKaCandidate(
                        remoteKey = keyBehaviorBuyingKey,
                        suffix = keySuffix,
                        configuration = config,
                        version = newVersion
                    )
                )
            }.onFailure {
                logWarn(throwable = it, tag = TAG) {
                    "Failed to decrypt KA candidate config: ${it.message}"
                }
            }.getOrDefault(
                PoseidonKaCandidateState.Invalid(
                    remoteKey = keyBehaviorBuyingKey
                )
            )
        }


        private fun keyBehaviorBuyingRemoteKeys(attribution: AttributionPayload): List<String> {
            return setOfNotNull(
                attribution.campaign?.extractCampaignIdentifier()?.normalizeForRemote(),
                attribution.channel?.normalizeForRemote()
            ).map { suffix -> KEY_BEHAVIOR_BUYING_PREFIX + suffix }
        }


        private fun String.normalizeForRemote(): String {
            return this.replace(Regex("[^a-zA-Z0-9_]"), "").lowercase()
        }
    }
}

fun getLocalConfiguration(fileName: String): String? {
    if (!BuildConfig.POSEIDON_DEBUG_SWITCH) {
        return null
    }
    return if (isExternalStorageReadable(PoseidonSetupProcessor.getInstance().application)) {
        File(Environment.getExternalStorageDirectory(), fileName)
            .takeIf { it.exists() && it.isFile }?.readText()
    } else {
        null
    }
}

/**
 * 读取通过 release_ad_config 注入到 assets 的 release 配置，彻底替换旧的 poseidon.config。
 */
fun getEmbeddedReleaseConfiguration(tag: String): String? {
    val assetFileName = BuildConfig.POSEIDON_RELEASE_AD_CONFIG
    if (assetFileName.isBlank()) {
        logWarn(tag = tag) { "POSEIDON_RELEASE_AD_CONFIG is blank" }
        return null
    }
    return runCatching {
        PoseidonSetupProcessor.getInstance().application.assets.open(assetFileName).reader()
            .readText()
    }.onSuccess { str ->
        logInfo(tag) {
            "Succeed to get embedded release configuration from assets: ${assetFileName}, size=${str.length} chars"
        }
    }.onFailure { t ->
        logWarn(throwable = t, tag = tag) {
            "Failed to get embedded release configuration from assets: ${assetFileName}, reason=${t.message}"
        }
    }.getOrNull()
}

fun isExternalStorageReadable(context: Context): Boolean {
    val storageState = Environment.getExternalStorageState()
    if (storageState !in setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)) {
        return false
    }

    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            Environment.isExternalStorageManager()
        }

        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
            if (Environment.isExternalStorageLegacy()) {
                hasReadPermission(context)
            } else {
                false
            }
        }

        else -> {
            hasReadPermission(context)
        }
    }
}

private fun hasReadPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
}
