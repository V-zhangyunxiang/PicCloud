package com.poseidon.ad.main.load

import com.poseidon.ad.main.strategy.limit.load.mode.AdLoadModeControlCfg

internal sealed class AdLoadTrafficOutcome {
    data object RunImmediately : AdLoadTrafficOutcome()
    data object EnqueueSerial : AdLoadTrafficOutcome()
    data class ScheduleDelay(val delayMillis: Long) : AdLoadTrafficOutcome()
}

internal fun evaluateAdLoadTrafficOutcome(
    cfg: AdLoadModeControlCfg?,
    adUnitId: String,
): AdLoadTrafficOutcome {
    if (cfg == null) {
        return AdLoadTrafficOutcome.RunImmediately
    }
    if (cfg.modeSerial?.adUnitIds?.contains(adUnitId) == true) {
        return AdLoadTrafficOutcome.EnqueueSerial
    }
    val fallbackMode = cfg.defaultMode()
    val delaySpec = cfg.modeDelay ?: return when (fallbackMode) {
        AdLoadTrafficControlMode.SERIAL -> AdLoadTrafficOutcome.EnqueueSerial
        AdLoadTrafficControlMode.DELAY -> AdLoadTrafficOutcome.EnqueueSerial
        AdLoadTrafficControlMode.IMMEDIATE -> AdLoadTrafficOutcome.RunImmediately
    }
    val perUnitDelay = delaySpec.adUnitIds?.get(adUnitId)
    return if (perUnitDelay == null) {
        when (fallbackMode) {
            AdLoadTrafficControlMode.SERIAL -> AdLoadTrafficOutcome.EnqueueSerial
            AdLoadTrafficControlMode.DELAY -> AdLoadTrafficOutcome.ScheduleDelay(delayMillis = delaySpec.timeInMillis)
            AdLoadTrafficControlMode.IMMEDIATE -> AdLoadTrafficOutcome.RunImmediately
        }
    } else {
        AdLoadTrafficOutcome.ScheduleDelay(delayMillis = perUnitDelay)
    }
}
