package com.poseidon.ad.main.processor

import android.os.SystemClock
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.core.log.LogHub
import com.poseidon.ad.main.adinfo.ERROR_CODE_US_NIGHT_BACKGROUND_PRELOAD
import com.poseidon.ad.statistics.biz.UnitResponseTracker
import kotlinx.atomicfu.atomic
import java.util.Calendar
import java.util.Locale

internal const val US_NIGHT_BACKGROUND_PRELOAD_MESSAGE = "US_NIGHT_BACKGROUND_PRELOAD"

internal object NightBackgroundPreloadGuard {
    private const val TAG = "NightBackgroundPreloadGuard"
    private const val US_COUNTRY_CODE = "US"
    private const val START_HOUR_INCLUSIVE = 2
    private const val END_HOUR_EXCLUSIVE = 6
    const val REMOTE_SWITCH_KEY = "poseidon_us_night_background_preload_guard_enabled"

    fun shouldSkipPreload(): Boolean {
        val remoteEnabledValue = PoseidonSetupProcessor.getInstance().remoteConfig.getString(REMOTE_SWITCH_KEY)
        val countryCode = Locale.getDefault().country
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isAppInForeground = PoseidonSetupProcessor.isAppInForeground
        val shouldSkip = shouldSkipPreload(
            remoteEnabledValue = remoteEnabledValue,
            isAppInForeground = isAppInForeground,
            countryCode = countryCode,
            hourOfDay = hourOfDay,
        )
        LogHub.info(TAG) {
            "night background preload guard input: " +
                "enabled=${nightBackgroundPreloadGuardEnabled(remoteEnabledValue)}(raw=$remoteEnabledValue), " +
                "foreground=$isAppInForeground, " +
                "countryCode=$countryCode, " +
                "hourOfDay=$hourOfDay, " +
                "shouldSkip=$shouldSkip"
        }
        return shouldSkip
    }

    internal fun shouldSkipPreload(
        remoteEnabledValue: String,
        isAppInForeground: Boolean,
        countryCode: String?,
        hourOfDay: Int,
    ): Boolean {
        return nightBackgroundPreloadGuardEnabled(remoteEnabledValue) &&
            !isAppInForeground &&
            countryCode.equals(US_COUNTRY_CODE, ignoreCase = true) &&
            hourOfDay in START_HOUR_INCLUSIVE until END_HOUR_EXCLUSIVE
    }

    private val lastSkipReportElapsedRealtime = atomic(0L)

    fun reportSkip(sceneName: String) {
        val now = SystemClock.elapsedRealtime()
        while (true) {
            val previous = lastSkipReportElapsedRealtime.value
            if (!isSkipReportDue(now, previous, PreloadMemoryGuard.SKIP_REPORT_INTERVAL_MILLIS)) {
                LogHub.info(TAG) {
                    "drop night background preload skip analytics: rate limited, scene=$sceneName"
                }
                return
            }
            if (lastSkipReportElapsedRealtime.compareAndSet(previous, now)) break
        }
        val message = usNightBackgroundPreloadMessage(sceneName)
        val sessionId = StringHelper.newSessionId()
        LogHub.info(TAG) {
            "send night background preload skip analytics: " +
                "scene=$sceneName, " +
                "sessionId=$sessionId, " +
                "message=$message"
        }
        UnitResponseTracker(
            sessionId = sessionId,
            sceneName = sceneName,
            resultCode = ERROR_CODE_US_NIGHT_BACKGROUND_PRELOAD,
            resultMessage = message,
        ).analyze()
    }
}

internal fun usNightBackgroundPreloadMessage(sceneName: String): String {
    return "${US_NIGHT_BACKGROUND_PRELOAD_MESSAGE}:sceneName:${sceneName}, code=${ERROR_CODE_US_NIGHT_BACKGROUND_PRELOAD}"
}

internal fun nightBackgroundPreloadGuardEnabled(remoteValue: String): Boolean {
    return remoteValue.toBooleanStrictOrNull() ?: false
}
