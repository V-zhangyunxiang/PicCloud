package com.poseidon.ad.common.storage.db

import com.poseidon.ad.common.storage.sp.KeyProvider
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.main.strategy.aeo.AeoCfg
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.main.strategy.rrc.RrcRoasCfg
import kotlinx.datetime.Clock

internal object RoomBackedPolicyState {
    private val frequencyEnabledKey = KeyProvider { "room_policy_frequency_enabled" }
    private val revenueEnabledKey = KeyProvider { "room_policy_revenue_enabled" }
    private val revenueEnabledAtKey = KeyProvider { "room_policy_revenue_enabled_at" }

    fun isFrequencyPersistenceEnabled(config: PoseidonConfig? = PoseidonConfig.get()): Boolean {
        val enabled = config?.globalControl?.frequencySwitch == true
        syncFrequencyState(enabled)
        return enabled
    }

    fun isRevenuePersistenceEnabled(): Boolean {
        val enabled = AeoCfg.get()?.isNotEmpty() == true || RrcRoasCfg.get()?.enabled == true
        syncRevenueState(enabled)
        return enabled
    }

    fun revenueQueryStart(requestedStart: Long): Long {
        val enabledAt = poseidonSetupProcessor().sharedPreference.getLong(revenueEnabledAtKey, 0L)
        return maxOf(requestedStart, enabledAt)
    }

    private fun syncFrequencyState(enabled: Boolean) {
        val sharedPreference = poseidonSetupProcessor().sharedPreference
        val wasEnabled = sharedPreference.getBoolean(frequencyEnabledKey, false)
        if (wasEnabled == enabled) return
        sharedPreference.putBoolean(frequencyEnabledKey, enabled)
        if (!enabled) {
            RoomPolicyDispatcher.dispatch {
                poseidonSetupProcessor().database().adFrequencyStatsDao().deleteAllFrequencyStats()
            }
        }
    }

    private fun syncRevenueState(enabled: Boolean) {
        val sharedPreference = poseidonSetupProcessor().sharedPreference
        val wasEnabled = sharedPreference.getBoolean(revenueEnabledKey, false)
        if (wasEnabled == enabled) return
        sharedPreference.putBoolean(revenueEnabledKey, enabled)
        if (enabled) {
            sharedPreference.putLong(revenueEnabledAtKey, Clock.System.now().toEpochMilliseconds())
        } else {
            sharedPreference.putLong(revenueEnabledAtKey, 0L)
            RoomPolicyDispatcher.dispatch {
                poseidonSetupProcessor().database().adExtraInfoDao().deleteAllReports()
            }
        }
    }
}
