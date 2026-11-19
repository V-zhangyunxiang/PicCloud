package com.poseidon.ad.common.storage.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update

/**
 * Poseidon 自研数据库，负责承载频控、展示限制和广告收入等本地存储。
 * Poseidon in-house Room database to host frequency, show limit, and revenue persistence.
 */
@Database(
    entities = [
        AdLimitShowInfoEntity::class,
        AdExtraInfoEntity::class,
        AdFrequencyStatsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PoseidonRoomDatabase : RoomDatabase() {
    abstract fun adLimitShowInfoDao(): AdLimitShowInfoDao
    abstract fun adExtraInfoDao(): AdExtraInfoDao
    abstract fun adFrequencyStatsDao(): AdFrequencyStatsDao

    companion object {
        const val DATABASE_NAME = "poseidon.db"
    }
}

/**
 * AdLimitShowInfo 表：记录每次展示资格判断的结果。
 * Table used to track whether a scene can show ads within a time range.
 */
@Entity(tableName = "AdLimitShowInfo")
data class AdLimitShowInfoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    val scene: String,
    val pass: Long,
    val time: Long,
    val shown: Long
)

/**
 * AdExtraInfo 表：记录各广告的收入指标。
 * Table storing every ad revenue snapshot.
 */
@Entity(tableName = "adExtraInfo")
data class AdExtraInfoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    val revenue: String,
    val ecpm: String,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    val precision: Long,
    @ColumnInfo(name = "network_id")
    val networkId: Long,
    @ColumnInfo(name = "ad_source_id")
    val adSourceId: Long,
    @ColumnInfo(name = "ad_source_placement_id")
    val adSourcePlacementId: String,
    @ColumnInfo(name = "ad_unit_id")
    val adUnitId: String,
    @ColumnInfo(name = "ad_type")
    val adType: Long,
    @ColumnInfo(name = "create_time")
    val createTime: Long
)

/**
 * 频控统计表，区分全局、场景、广告类型等维度。
 * Tracks frequency stats grouped by dimension (global/scene/ad type).
 */
@Entity(
    tableName = "AdFrequencyStats",
    indices = [
        Index(value = ["day_key", "level", "identifier"], unique = true),
        Index(value = ["day_key", "level"]),
        Index(value = ["identifier"])
    ]
)
data class AdFrequencyStatsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "day_key")
    val dayKey: String,
    val level: String,
    val identifier: String,
    @ColumnInfo(name = "display_count")
    val displayCount: Long = 0,
    @ColumnInfo(name = "click_count")
    val clickCount: Long = 0,
    @ColumnInfo(name = "last_display_time")
    val lastDisplayTime: Long = 0,
)

@Dao
interface AdLimitShowInfoDao {
    @Insert
    suspend fun insert(entity: AdLimitShowInfoEntity): Long

    @Query("UPDATE AdLimitShowInfo SET shown = :shown WHERE _id = :id")
    suspend fun updateShownById(shown: Long, id: Long)

    @Query("SELECT COUNT(*) FROM AdLimitShowInfo WHERE scene = :scene AND time BETWEEN :start AND :end")
    suspend fun countBetween(scene: String, start: Long, end: Long): Long

    @Query(
        "SELECT * FROM AdLimitShowInfo WHERE scene = :scene AND time BETWEEN :start AND :end " +
                "ORDER BY time ASC LIMIT 1"
    )
    suspend fun firstBetween(scene: String, start: Long, end: Long): AdLimitShowInfoEntity?

    @Query(
        "SELECT * FROM AdLimitShowInfo WHERE scene = :scene AND time BETWEEN :start AND :end " +
                "ORDER BY time DESC LIMIT :count"
    )
    suspend fun recentBetween(scene: String, start: Long, end: Long, count: Long): List<AdLimitShowInfoEntity>

    @Query("SELECT * FROM AdLimitShowInfo WHERE time <= :end")
    suspend fun queryBefore(end: Long): List<AdLimitShowInfoEntity>

    @Query("DELETE FROM AdLimitShowInfo WHERE _id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AdExtraInfoDao {
    @Insert
    suspend fun insert(record: AdExtraInfoEntity)

    @Query("DELETE FROM adExtraInfo WHERE create_time < :beforeTimestamp")
    suspend fun deleteOldReports(beforeTimestamp: Long)

    @Query("DELETE FROM adExtraInfo")
    suspend fun deleteAllReports()

    @Query("SELECT * FROM adExtraInfo WHERE create_time BETWEEN :fromTime AND :toTime")
    suspend fun getReportsBetween(fromTime: Long, toTime: Long): List<AdExtraInfoEntity>
}

@Dao
abstract class AdFrequencyStatsDao {
    @Query("SELECT * FROM AdFrequencyStats WHERE day_key = :dayKey AND level = :level AND identifier = :identifier LIMIT 1")
    abstract suspend fun getFrequencyStats(
        dayKey: String,
        level: String,
        identifier: String
    ): AdFrequencyStatsEntity?

    @Insert
    protected abstract suspend fun insert(entity: AdFrequencyStatsEntity): Long

    @Update
    protected abstract suspend fun update(entity: AdFrequencyStatsEntity)

    @Query("DELETE FROM AdFrequencyStats WHERE day_key < :dayKey")
    abstract suspend fun deleteStatsBefore(dayKey: String)

    @Query("SELECT * FROM AdFrequencyStats ORDER BY day_key DESC, level, identifier")
    abstract suspend fun getAllFrequencyStats(): List<AdFrequencyStatsEntity>

    @Query("DELETE FROM AdFrequencyStats")
    abstract suspend fun deleteAllFrequencyStats()

    /**
     * 自增展示次数。
     * Increase display counter and refresh last display time atomically.
     */
    @Transaction
    open suspend fun insertOrUpdateDisplayCount(
        dayKey: String,
        level: String,
        identifier: String,
        lastDisplayTime: Long
    ) {
        val current = getFrequencyStats(dayKey, level, identifier)
        if (current == null) {
            insert(
                AdFrequencyStatsEntity(
                    dayKey = dayKey,
                    level = level,
                    identifier = identifier,
                    displayCount = 1,
                    clickCount = 0,
                    lastDisplayTime = lastDisplayTime
                )
            )
        } else {
            update(
                current.copy(
                    displayCount = current.displayCount + 1,
                    lastDisplayTime = lastDisplayTime
                )
            )
        }
    }

    /**
     * 自增点击次数。
     * Increase click counter without touching last display time.
     */
    @Transaction
    open suspend fun insertOrUpdateClickCount(
        dayKey: String,
        level: String,
        identifier: String
    ) {
        val current = getFrequencyStats(dayKey, level, identifier)
        if (current == null) {
            insert(
                AdFrequencyStatsEntity(
                    dayKey = dayKey,
                    level = level,
                    identifier = identifier,
                    displayCount = 0,
                    clickCount = 1,
                    lastDisplayTime = 0
                )
            )
        } else {
            update(
                current.copy(
                    clickCount = current.clickCount + 1
                )
            )
        }
    }
}
