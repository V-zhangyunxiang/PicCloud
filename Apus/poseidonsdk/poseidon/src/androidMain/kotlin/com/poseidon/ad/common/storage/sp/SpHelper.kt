package com.poseidon.ad.common.storage.sp

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.AnyThread
import androidx.annotation.Keep
import androidx.core.content.edit

/**
 * SharedPreferences 通用管理器，提供线程安全的多类型读写能力。
 */
@Keep
class SpHelper(private val context: Context) {

    // 全局锁，保障跨线程访问安全。
    private val lock = Any()

    /**
     * 读取布尔值。
     */
    fun getBoolean(key: KeyProvider, defValue: Boolean): Boolean =
        withPrefs { getBoolean(key.key(), defValue) }

    /**
     * 写入布尔值。
     */
    fun putBoolean(key: KeyProvider, value: Boolean) =
        editPrefs { putBoolean(key.key(), value) }

    /**
     * 读取 Double 值（字符串存储方式）。
     */
    fun getDouble(key: KeyProvider, defValue: Double): Double =
        withPrefs { getString(key.key(), "")?.toDoubleOrNull() ?: defValue }

    /**
     * 写入 Double 值（字符串存储方式）。
     */
    fun putDouble(key: KeyProvider, value: Double) =
        editPrefs { putString(key.key(), value.toString()) }

    /**
     * 读取整型值。
     */
    fun getInt(key: KeyProvider, defValue: Int): Int =
        withPrefs { getInt(key.key(), defValue) }

    /**
     * 写入整型值。
     */
    fun putInt(key: KeyProvider, value: Int) =
        editPrefs { putInt(key.key(), value) }

    /**
     * 读取字符串。
     */
    fun getString(key: KeyProvider, defValue: String?): String? =
        withPrefs { getString(key.key(), defValue) }

    /**
     * 写入字符串。
     */
    fun putString(key: KeyProvider, value: String?) =
        editPrefs { putString(key.key(), value) }

    /**
     * 读取 Long 值。
     */
    fun getLong(key: KeyProvider, defValue: Long): Long =
        withPrefs { getLong(key.key(), defValue) }

    /**
     * 写入 Long 值。
     */
    fun putLong(key: KeyProvider, value: Long) =
        editPrefs { putLong(key.key(), value) }

    /**
     * 对 SharedPreferences 的读取操作封装，统一加锁逻辑。
     */
    private fun <T> withPrefs(action: SharedPreferences.() -> T): T {
        val prefs = sharedPreferencesInstance(context)
        return synchronized(lock) { prefs.action() }
    }

    /**
     * 对 SharedPreferences 的写入操作封装，统一加锁 & 提交方式。
     */
    private fun editPrefs(block: SharedPreferences.Editor.() -> Unit) {
        val prefs = sharedPreferencesInstance(context)
        synchronized(lock) {
            prefs.edit {
                block(this)
            }
        }
    }

    /**
     * 批量写入多条 SP 条目，仅触发一次 apply()。
     */
    fun writeBatch(entries: List<SpBatchEntry>) {
        if (entries.isEmpty()) return
        val prefs = sharedPreferencesInstance(context)
        synchronized(lock) {
            prefs.edit {
                for (entry in entries) {
                    when (entry) {
                        is SpBatchEntry.BooleanValue -> putBoolean(entry.key.key(), entry.value)
                        is SpBatchEntry.IntValue -> putInt(entry.key.key(), entry.value)
                        is SpBatchEntry.LongValue -> putLong(entry.key.key(), entry.value)
                        is SpBatchEntry.StringValue -> putString(entry.key.key(), entry.value)
                    }
                }
            }
        }
    }

    companion object {
        @Volatile
        private var sharedPreferences: SharedPreferences? = null

        /**
         * 获取全局 SharedPreferences 实例，使用双重检查锁保证懒加载与线程安全。
         */
        @AnyThread
        private fun sharedPreferencesInstance(context: Context): SharedPreferences {
            if (sharedPreferences == null) {
                synchronized(SpHelper::class.java) {
                    if (sharedPreferences == null) {
                        sharedPreferences = context.getSharedPreferences("poseidon_sp", Context.MODE_PRIVATE)
                    }
                }
            }
            return sharedPreferences!!
        }
    }
}
