package com.poseidon.ad.common.toolkit

import com.poseidon.ad.common.storage.sp.KeyProvider
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.restrict.show.currentDateFormatWithYearMonthDay

internal const val DATE_SUFFIX = "_#with_save_date_in_year_month_day#"

/**
 * 每日重置协调器，封装重复的日期存储逻辑。
 */
internal class DateHelper(
    private val keyProvider: KeyProvider,
    private val onReset: () -> Unit
) {

    private val storage by lazy { PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference }

    /**
     * 对外暴露的重置入口，负责读取/比较/写入日期。
     */
    fun resetDate() {
        val snapshot = buildSnapshot()
        if (snapshot.shouldUpdate()) {
            logInfo("DateHelper") {
                "Daily reset triggered for key=${snapshot.storageKey}; last=${snapshot.last}, today=${snapshot.today}"
            }
            storage.putString(key = snapshot.storageKey.toKeyProvider(), value = snapshot.today)
            onReset.invoke()
        }
    }

    /**
     * 构建当前重置所需的状态快照。
     */
    private fun buildSnapshot(): ResetSnapshot {
        val today = currentDateFormatWithYearMonthDay()
        val storageKey = keyProvider.dailyKey()
        val lastSaved = storage.getString(key = storageKey.toKeyProvider(), defValue = null)
        return ResetSnapshot(storageKey, lastSaved, today)
    }
}

/**
 * 存储当前重置的上下文，用于判定是否需要更新。
 */
private data class ResetSnapshot(
    val storageKey: String,
    val last: String?,
    val today: String
) {
    fun shouldUpdate(): Boolean = last.isNullOrEmpty() || last != today
}

/**
 * 生成特定日期后缀的 key，确保不同业务之间不会冲突。
 */
private fun KeyProvider.dailyKey(): String = key() + DATE_SUFFIX

/**
 * 将字符串包装成 KeyProvider，以复用 SharedPreference 的 API。
 */
private fun String.toKeyProvider(): KeyProvider {
    val value = this
    return KeyProvider { value }
}
