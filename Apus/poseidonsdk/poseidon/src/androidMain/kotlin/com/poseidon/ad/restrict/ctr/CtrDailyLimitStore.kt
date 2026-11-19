package com.poseidon.ad.restrict.ctr

import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.storage.sp.SpBatchEntry
import com.poseidon.ad.common.storage.sp.computeDailyCounterPlan
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.restrict.show.currentDateFormatWithYearMonthDay

/**
 * CTR 每日状态存储，对接 SharedPreferences。
 */
internal object CtrDailyLimitStore {

    private val sharedPreference get() = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference

    /**
     * 在日期变化时重置标记并更新日期。
     */
    fun resetIfNewDay() {
        val lastDate = sharedPreference.getString(Key.CtrTooHighLastDate, null)
        val currentDate = currentDateFormatWithYearMonthDay()
        val plan = computeDailyCounterPlan(
            savedDate = lastDate,
            currentDate = currentDate,
            savedCounter = 0,
            applyIncrement = false,
        )
        if (plan.needsPersistDate) {
            sharedPreference.writeBatch(listOf(
                SpBatchEntry.BooleanValue(key = Key.CtrIsGreatThanLimit, value = false),
                SpBatchEntry.StringValue(key = Key.CtrTooHighLastDate, value = plan.dateValue),
            ))
        }
    }

    /**
     * 读取是否已经触发禁播。
     */
    fun isLimitReached(): Boolean {
        return sharedPreference.getBoolean(Key.CtrIsGreatThanLimit, false)
    }

    /**
     * 标记当天已达上限。
     */
    fun markLimitReached() {
        sharedPreference.putBoolean(Key.CtrIsGreatThanLimit, true)
    }
}
