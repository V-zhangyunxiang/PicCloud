package com.poseidon.ad.main.strategy.aeo

import android.os.Environment
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_NATIVE
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.strategy.cryptor.Cryptor
import com.poseidon.ad.restrict.traffic.TrafficLimit
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.storage.sp.KeyProvider
import com.poseidon.ad.cloud.FirebaseCloudException
import com.poseidon.ad.common.storage.db.AD_REVENUE_REPOSITORY_IMPL
import com.poseidon.ad.cloud.throwRemoteConfigurationFetchException
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.statistics.SupportAnalyticsBackend
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.cloud.FirebaseCloudUpdateListener
import com.poseidon.ad.main.strategy.common.isExternalStorageReadable
import com.poseidon.ad.common.toolkit.logInfo
import java.io.File
import kotlin.jvm.JvmStatic

private const val AEO_TRACKER_TAG = "AeoTracker"

@Serializable
internal data class AeoCfg(
    @SerialName("pos_price") val price: Double,
    @SerialName("pos_time") val time: Int,
    @SerialName("pos_hour") val hour: Int,
    @SerialName("pos_eventToken") val adjustToken: String,
    @SerialName("pos_firebaseEvent") val firebaseEventName: String,
    @SerialName("pos_adTypes") val adTypes: String? = "n,r,i",
) {

    val resolvedAdTypes: List<Int> by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        val default = listOf(AD_TYPE_NATIVE, AD_TYPE_REWARDED, AD_TYPE_INTERSTITIAL)
        runCatching {
            adTypes?.split(",")
                ?.asSequence()
                ?.map { it.trim() }
                ?.filterNot { it.isEmpty() }
                ?.map {
                    when (it) {
                        "n" -> AD_TYPE_NATIVE
                        "r" -> AD_TYPE_REWARDED
                        "i" -> AD_TYPE_INTERSTITIAL
                        else -> null
                    }
                }
                ?.filterNotNull()
                ?.toList()
                ?: default
        }.getOrDefault(default)
    }

    companion object : FirebaseCloudUpdateListener {
        private const val TAG = "AeoCfg"
        private const val REMOTE_KEY = "poseidon_aeo_track_cfg"

        @Volatile
        private var instance: List<AeoCfg>? = null
        private val lock = ReentrantLock()

        @Suppress("SpellCheckingInspection")
        private val cryptor: Cryptor by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { Cryptor() }

        @JvmStatic
        fun get(): List<AeoCfg>? {
            if (instance == null) {
                lock.withLock {
                    if (instance == null) {
                        instance = fromRemote()
                    }
                }
            }
            return instance
        }

        private fun fromRemote(): List<AeoCfg>? {
            val poseidonSetupProcessor = poseidonSetupProcessor()
            return runCatching {

                @Throws(exceptionClasses = [IllegalStateException::class])
                fun remoteConfiguration(): String {
                    val str = poseidonSetupProcessor.remoteConfig.getString(REMOTE_KEY)
                    val decryptedStr = str.ifEmpty {
                        throwRemoteConfigurationFetchException("The AEO configuration was not obtained from the Remote")
                    }
                    return cryptor.decrypt(decryptedStr)
                }

                val json = if (poseidonSetupProcessor.debug) {
                    getAeoLocalConfiguration(fileName = "${REMOTE_KEY}.json").takeUnless { it.isNullOrEmpty() }
                        ?: remoteConfiguration()
                } else {
                    remoteConfiguration()
                }

                PoseidonJson.decodeFromString<List<AeoCfg>>(json)
            }.onFailure { t ->
                if (t is FirebaseCloudException) {
                    if (TrafficLimit.isPassable(remoteKey = "AeoConfigParseError_error_detection", defaultScale = 30)) {
                        ErrorDetectionReporter(
                            name = "AeoConfigParseError",
                            extra = t.message.orEmpty()
                        ).analyze()
                    }
                } else {
                    ErrorDetectionReporter(
                        name = "AeoConfigParseError",
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

fun getAeoLocalConfiguration(fileName: String): String? {
    return if (isExternalStorageReadable(PoseidonSetupProcessor.getInstance().application)) {
        File(Environment.getExternalStorageDirectory(), fileName)
            .takeIf { it.exists() && it.isFile }?.readText()
    } else {
        null
    }
}

fun trackAdjustAeo(token: String) {
    val adjustEvent = AdjustEvent(token)
    Adjust.trackEvent(adjustEvent)
}

fun trackFirebaseAeo(eventName: String) {
    PoseidonSetupProcessor.getInstance().analyticsFactory.obtain(by = SupportAnalyticsBackend.Firebase).logEvent(
        name = eventName,
        parameters = mapOf()
    )
}

internal suspend fun trackAeo() {
    fun AeoCfg.toKey(): KeyProvider = KeyProvider {
        Key.AeoTracking.name + "_" + adjustToken + "_" + firebaseEventName
    }

    val setupProcessor = poseidonSetupProcessor()
    val sharedPreference = setupProcessor.sharedPreference
    val userFirstOpenTime = setupProcessor.awaitNewUserFirstOpenTime()
    if (userFirstOpenTime == null || userFirstOpenTime <= 0L) {
        logInfo(AEO_TRACKER_TAG) { "First-open timeline unavailable, skip AEO tracking" }
        return
    }
    AeoCfg.get()
        ?.filterNot { config -> sharedPreference.getBoolean(config.toKey(), false) }
        ?.forEach { config ->
            val timeCycle = config.hour
            val firstOpenInstant = Instant.fromEpochMilliseconds(userFirstOpenTime)
            val periodEnd = firstOpenInstant
                .plus(timeCycle, DateTimeUnit.HOUR, TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            val count = AD_REVENUE_REPOSITORY_IMPL.count(
                fromTime = userFirstOpenTime,
                toTime = periodEnd,
                revenueFloor = config.price,
                supportAdTypes = config.resolvedAdTypes
            )
            if (count >= config.time) {
                val adjustToken = config.adjustToken
                if (adjustToken.isNotEmpty()) {
                    trackAdjustAeo(token = adjustToken)
                }
                val firebaseEventName = config.firebaseEventName
                if (firebaseEventName.isNotEmpty()) {
                    trackFirebaseAeo(eventName = firebaseEventName)
                }
                sharedPreference.putBoolean(config.toKey(), true)
            }
        }
}
