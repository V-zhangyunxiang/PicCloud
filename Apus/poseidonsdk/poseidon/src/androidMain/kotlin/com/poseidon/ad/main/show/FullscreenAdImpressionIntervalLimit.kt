package com.poseidon.ad.main.show

import android.os.Environment
import androidx.annotation.VisibleForTesting
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.cloud.FirebaseCloudUpdateListener
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.main.adinfo.FullscreenAdTypeDef
import com.poseidon.ad.main.adinfo.isFullscreenAd
import com.poseidon.ad.main.strategy.common.isExternalStorageReadable
import com.poseidon.ad.main.strategy.cryptor.Cryptor
import com.poseidon.ad.restrict.traffic.TrafficLimit
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

object FullscreenAdImpressionIntervalLimit {
    
    private const val TAG = "FullscreenAdImpressionIntervalLimit"

    fun callOnAdClosed(@FullscreenAdTypeDef fullscreenAdType: Int) {
        val resolvedLimitedFullscreenAdTypes =
            resolveLimitedFullscreenAdTypes(FullscreenAdImpressionIntervalLimitConfiguration.get()?.limitAdTypesWhenAdClosed)
        logInfo(TAG) {
            "callOnAdClosed: fullscreenAdType=$fullscreenAdType, resolvedLimitedFullscreenAdTypes=${
                resolvedLimitedFullscreenAdTypes.joinToString(
                    ","
                )
            }"
        }
        if (resolvedLimitedFullscreenAdTypes.contains(fullscreenAdType)) {
            poseidonSetupProcessor().sharedPreference.putLong(
                Key.LimitedFullscreenAdLastClosedTimeInMillisV2,
                TimeHelper.currentTimeInMillis()
            )
        }
    }

    @VisibleForTesting
    internal fun resolveLimitedFullscreenAdTypes(str: String?): List<Int> {
        val default = emptyList<Int>()
        return runCatching {
            str?.trim()?.takeUnless { it.isEmpty() }
                ?.let { str ->
                    if (str.contains(",")) {
                        str.split(",")
                            .asSequence()
                            .map { it.trim() }
                            .filterNot { it.isEmpty() }
                            .map { it.toIntOrNull() }
                            .filterNotNull()
                            .filter { isFullscreenAd(adType = it) }
                            .toList()
                            .takeIf { it.isNotEmpty() } ?: default
                    } else {
                        str.toIntOrNull()
                            ?.takeIf { isFullscreenAd(adType = it) }
                            ?.let { listOf(it) }
                            ?: default
                    }
                } ?: default
        }.getOrDefault(default)
    }

    private fun getLimitIntervalInMillisFromRemote(): Long {
        return FullscreenAdImpressionIntervalLimitConfiguration.get()?.intervalInMillis ?: -1
    }

    private fun getLimitIntervalInMillisByDayFromRemote() : Map<Int, Long>? {
        fun String.resolveIntervalInMillisByDay() : Pair<Int, Long>? {
            return if (contains("_")) {
                val split = split("_")
                if (split.size == 2) {
                    val day = split[0].toIntOrNull()
                    val intervalInMillis = split[1].toLongOrNull()
                    if (day == null || intervalInMillis == null) {
                        null
                    } else {
                        day to intervalInMillis
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }
        val intervalInMillisByDay = FullscreenAdImpressionIntervalLimitConfiguration.get()?.intervalInMillisByDay
        if (intervalInMillisByDay.isNullOrEmpty()) {
            return null
        }
        return if (intervalInMillisByDay.contains(",")) {
            intervalInMillisByDay.split(",")
                .asSequence()
                .map { it.trim() }
                .mapNotNull { it.resolveIntervalInMillisByDay() }
                .toList()
                .toMap()
        } else {
            val resolved = intervalInMillisByDay.resolveIntervalInMillisByDay() ?: return null
            mapOf(resolved)
        }
    }

    private fun calculateLimitIntervalInMillisByDay(): Long? {
        val limitIntervalInMillisByDayFromRemote = getLimitIntervalInMillisByDayFromRemote()
        if (limitIntervalInMillisByDayFromRemote.isNullOrEmpty()) {
            logInfo(TAG) {
                "calculateLimitIntervalInMillisByDay: limitIntervalInMillisByDayFromRemote is null"
            }
            return null
        }

        val newUserFirstOpenTime = poseidonSetupProcessor().newUserFirstOpenTime()
        logInfo(TAG) {
            "calculateLimitIntervalInMillisByDay: newUserFirstOpenTime=$newUserFirstOpenTime"
        }
        if (newUserFirstOpenTime <= 0) {
            ErrorDetectionReporter(
                name = "FAIILC_calculateLimitIntervalInMillisByDay",
                extra = "newUserFirstOpenTime <= 0: $newUserFirstOpenTime"
            ).analyze()
            return null
        }
        val newUserFirstOpenInstant = Instant.fromEpochMilliseconds(newUserFirstOpenTime)
        val nowInstant = Clock.System.now()
        val days = newUserFirstOpenInstant.daysUntil(nowInstant, TimeZone.currentSystemDefault())
        val key = limitIntervalInMillisByDayFromRemote.keys.filter { it >= days }.minOrNull()
        val limitIntervalInMillis = if (key != null) {
            limitIntervalInMillisByDayFromRemote[key]
        } else {
            null
        }
        logInfo(TAG) {
            "calculateLimitIntervalInMillisByDay: newUserFirstOpenTime=$newUserFirstOpenTime, newUserFirstOpenInstant=${newUserFirstOpenInstant.format()}, nowInstant=${nowInstant.format()}, days=${days}, key=$key, limitIntervalInMillis=$limitIntervalInMillis"
        }
        return limitIntervalInMillis
    }

    private fun isDefaultLimitStrategyEnabled(): Boolean {
        return FullscreenAdImpressionIntervalLimitConfiguration.get()?.strategies?.default?.switch
            ?: false
    }

    private fun isLadybugLimitStrategyEnabled(): Boolean {
        val ladybug = FullscreenAdImpressionIntervalLimitConfiguration.get()?.strategies?.ladybug
            ?: return false
        return if (ladybug.switch) {
            val limitUntilNewUserInstalledInDays = ladybug.limitUntilNewUserInstalledInDays
            logInfo(TAG) {
                "isLadybugLimitStrategyEnabled: limitUntilNewUserInstalledInDays=$limitUntilNewUserInstalledInDays"
            }
            if (limitUntilNewUserInstalledInDays <= 0) {
                ErrorDetectionReporter(
                    name = "FAIILC_isLadybugLimitStrategyEnabled",
                    extra = "limitUntilNewUserInstalledInDays <= 0: $limitUntilNewUserInstalledInDays"
                ).analyze()
                return false
            }
            val newUserFirstOpenTime = poseidonSetupProcessor().newUserFirstOpenTime()
            logInfo(TAG) {
                "isLadybugLimitStrategyEnabled: newUserFirstOpenTime=$newUserFirstOpenTime"
            }
            if (newUserFirstOpenTime <= 0) {
                ErrorDetectionReporter(
                    name = "FAIILC_isLadybugLimitStrategyEnabled",
                    extra = "newUserFirstOpenTime <= 0: $newUserFirstOpenTime"
                ).analyze()
                return false
            }
            val newUserFirstOpenInstant = Instant.fromEpochMilliseconds(newUserFirstOpenTime)
            val endInstant = newUserFirstOpenInstant
                .plus(
                    limitUntilNewUserInstalledInDays,
                    DateTimeUnit.DAY,
                    TimeZone.currentSystemDefault()
                )
            val nowInstant = Clock.System.now()
            logInfo(TAG) {
                "isLadybugLimitStrategyEnabled: newUserFirstOpenTime=$newUserFirstOpenTime, newUserFirstOpenInstant=${newUserFirstOpenInstant.format()}, endInstant=${endInstant.format()}, nowInstant=${nowInstant.format()}"
            }
            nowInstant <= endInstant
        } else {
            logInfo(TAG) {
                "isLadybugLimitStrategyEnabled: switch is off"
            }
            false
        }
    }

    private fun Instant.format(): String {
        return toLocalDateTime(TimeZone.currentSystemDefault()).toString()
    }

    fun canShowAd(@FullscreenAdTypeDef fullScreenAdType: Int): Boolean {
        val resolvedLimitedFullscreenAdTypes =
            resolveLimitedFullscreenAdTypes(FullscreenAdImpressionIntervalLimitConfiguration.get()?.limitAdTypesWhenAdCanShow)
        if (resolvedLimitedFullscreenAdTypes.contains(fullScreenAdType)) {
            val poseidonSetupProcessor = poseidonSetupProcessor()
            val limitInterval = calculateLimitIntervalInMillisByDay() ?: getLimitIntervalInMillisFromRemote()
            if (limitInterval <= 0) {
                logInfo(TAG) {
                    "canShowAd[true]: fullscreenAdType=$fullScreenAdType, resolvedLimitedFullscreenAdTypes=${
                        resolvedLimitedFullscreenAdTypes.joinToString(
                            ","
                        )
                    }, limitInterval=$limitInterval"
                }
                return true
            }

            val defaultLimitStrategyEnabled = isDefaultLimitStrategyEnabled()
            val ladybugLimitStrategyEnabled = isLadybugLimitStrategyEnabled()
            if (defaultLimitStrategyEnabled || ladybugLimitStrategyEnabled) {
                val last =
                    poseidonSetupProcessor.sharedPreference.getLong(Key.LimitedFullscreenAdLastClosedTimeInMillisV2, -1L)
                if (last < 0) {
                    logInfo(TAG) {
                        "canShowAd[true]: fullscreenAdType=$fullScreenAdType, resolvedLimitedFullscreenAdTypes=${
                            resolvedLimitedFullscreenAdTypes.joinToString(
                                ","
                            )
                        }, limitInterval=$limitInterval, defaultLimitStrategyEnabled=$defaultLimitStrategyEnabled, ladybugLimitStrategyEnabled=$ladybugLimitStrategyEnabled, limitedFullscreenAdLastClosedTimeInMillis=$last"
                    }
                    return true
                }

                val now = TimeHelper.currentTimeInMillis()
                if (now >= last) {
                    val currentInterval = now - last
                    val greatThanLimitInterval = currentInterval > limitInterval
                    logInfo(TAG) {
                        "canShowAd[${greatThanLimitInterval}]: fullscreenAdType=$fullScreenAdType, resolvedLimitedFullscreenAdTypes=${
                            resolvedLimitedFullscreenAdTypes.joinToString(
                                ","
                            )
                        }, currentInterval=$currentInterval, limitInterval=$limitInterval, defaultLimitStrategyEnabled=$defaultLimitStrategyEnabled, ladybugLimitStrategyEnabled=$ladybugLimitStrategyEnabled, now=$now, limitedFullscreenAdLastClosedTimeInMillis=$last"
                    }
                    return greatThanLimitInterval
                } else {
                    logInfo(TAG) {
                        "canShowAd[true]: fullscreenAdType=$fullScreenAdType, resolvedLimitedFullscreenAdTypes=${
                            resolvedLimitedFullscreenAdTypes.joinToString(
                                ","
                            )
                        }, limitInterval=$limitInterval, defaultLimitStrategyEnabled=$defaultLimitStrategyEnabled, ladybugLimitStrategyEnabled=$ladybugLimitStrategyEnabled, now=$now, limitedFullscreenAdLastClosedTimeInMillis=$last"
                    }
                    return true
                }
            } else {
                logInfo(TAG) {
                    "canShowAd[true]: fullscreenAdType=$fullScreenAdType, resolvedLimitedFullscreenAdTypes=${
                        resolvedLimitedFullscreenAdTypes.joinToString(
                            ","
                        )
                    }, limitInterval=$limitInterval, defaultLimitStrategyEnabled=$defaultLimitStrategyEnabled, ladybugLimitStrategyEnabled=$ladybugLimitStrategyEnabled"
                }
                return true
            }
        } else {
            logInfo(TAG) {
                "canShowAd[true]: fullscreenAdType=$fullScreenAdType, resolvedLimitedFullscreenAdTypes=${
                    resolvedLimitedFullscreenAdTypes.joinToString(
                        ","
                    )
                }"
            }
            return true
        }
    }
}

@Serializable
internal data class LimitStrategies(
    @SerialName("pos_default") val default: DefaultLimitStrategy,
    @SerialName("pos_ladybug") val ladybug: LadybugLimitStrategy,
)

@Serializable
internal data class DefaultLimitStrategy(
    @SerialName("pos_switch") val switch: Boolean,
)

@Serializable
internal data class LadybugLimitStrategy(
    @SerialName("pos_switch") val switch: Boolean,
    @SerialName("pos_limit_until_new_user_installed_in_days") val limitUntilNewUserInstalledInDays: Int,
)

@Serializable
internal data class FullscreenAdImpressionIntervalLimitConfiguration(
    @SerialName("pos_limit_ad_types_when_ad_closed") val limitAdTypesWhenAdClosed: String,
    @SerialName("pos_limit_ad_types_when_ad_can_show") val limitAdTypesWhenAdCanShow: String,
    @SerialName("pos_interval_in_millis") val intervalInMillis: Long,
    @SerialName("pos_interval_in_millis_by_day") val intervalInMillisByDay: String?,
    @SerialName("pos_strategies") val strategies: LimitStrategies,
) {
    companion object : FirebaseCloudUpdateListener {
        private const val TAG = "FullscreenAdImpressionIntervalLimitConfiguration"
        private const val REMOTE_KEY = "poseidon_ad_show_interval_limit_config"

        @Volatile
        private var instance: FullscreenAdImpressionIntervalLimitConfiguration? = null
        private val lock = ReentrantLock()

        @Suppress("SpellCheckingInspection")
        private val cryptor: Cryptor by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { Cryptor() }

        @JvmStatic
        fun get(): FullscreenAdImpressionIntervalLimitConfiguration? {
            if (instance == null) {
                lock.withLock {
                    if (instance == null) {
                        instance = fromRemote()
                    }
                }
            }
            return instance
        }

        private fun fromRemote(): FullscreenAdImpressionIntervalLimitConfiguration? {
            val poseidonSetupProcessor = poseidonSetupProcessor()
            return runCatching {

                @Throws(exceptionClasses = [IllegalStateException::class])
                fun remoteConfiguration(): String {
                    val str = poseidonSetupProcessor.remoteConfig.getString(REMOTE_KEY)
                    val decryptedStr = str.ifEmpty {
                        error("The FullscreenAdImpressionIntervalLimit configuration was not obtained from the Remote")
                    }
                    return cryptor.decrypt(decryptedStr)
                }

                val json = if (poseidonSetupProcessor.debug) {
                    getFullscreenAdImpressionIntervalLimitConfiguration(fileName = "${REMOTE_KEY}.json").takeUnless { it.isNullOrEmpty() }
                        ?: remoteConfiguration()
                } else {
                    remoteConfiguration()
                }

                PoseidonJson.decodeFromString<FullscreenAdImpressionIntervalLimitConfiguration>(json)
            }.onFailure { t ->
                if (TrafficLimit.isPassable(remoteKey = "FullscreenAdImpressionIntervalLimitConfigParseError_error_detection", defaultScale = 30)) {
                    ErrorDetectionReporter(
                        name = "FullscreenAdImpressionIntervalLimitConfigParseError",
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

internal fun getFullscreenAdImpressionIntervalLimitConfiguration(fileName: String): String? {
    return if (isExternalStorageReadable(PoseidonSetupProcessor.getInstance().application)) {
        File(Environment.getExternalStorageDirectory(), fileName)
            .takeIf { it.exists() && it.isFile }?.readText()
    } else {
        null
    }
}
