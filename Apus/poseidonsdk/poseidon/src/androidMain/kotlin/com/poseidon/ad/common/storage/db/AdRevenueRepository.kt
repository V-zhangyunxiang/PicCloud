package com.poseidon.ad.common.storage.db

import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor

/**
 * 广告收入仓储接口，提供跨模块复用的增删查能力。
 */
internal interface AdRevenueRepository {
    /**
     * 插入一条广告收入记录。
     */
    suspend fun insert(record: AdRevenueRecord)

    /**
     * 删除小于指定时间戳的历史记录。
     */
    suspend fun deleteOldReports(beforeTimestamp: Long)

    /**
     * Counts the number of ad records within [fromTime, toTime] whose revenue is greater than or equal to [revenueFloor]
     * and whose adType is contained in [supportAdTypes].
     */
    suspend fun count(
        fromTime: Long,
        toTime: Long,
        revenueFloor: Double,
        supportAdTypes: List<Int>
    ): Int

    /**
     * 统计 [fromTime, toTime] 区间内广告收入的总和。
     */
    suspend fun sumRevenue(
        fromTime: Long,
        toTime: Long
    ): Double
}

/**
 * 默认实现，所有数据库访问都封装在 [RevenueQueryExecutor] 中。
 */
internal object AD_REVENUE_REPOSITORY_IMPL : AdRevenueRepository {
    // 查询执行器，集中负责数据库 CRUD。
    private val executor = RevenueQueryExecutor()

    override suspend fun insert(record: AdRevenueRecord) {
        executor.persistRecord(record)
    }

    override suspend fun deleteOldReports(beforeTimestamp: Long) {
        executor.prune(beforeTimestamp)
    }

    override suspend fun count(
        fromTime: Long,
        toTime: Long,
        revenueFloor: Double,
        supportAdTypes: List<Int>
    ): Int {
        if (supportAdTypes.isEmpty()) return 0
        val typeSet = supportAdTypes.toHashSet()
        val reports = executor.fetchReports(RoomBackedPolicyState.revenueQueryStart(fromTime), toTime)
        return reports.fold(0) { acc, row ->
            val revenueValue = row.revenue.toDoubleOrNull() ?: 0.0
            val adTypeValue = row.adType.toInt()
            if (revenueValue >= revenueFloor && typeSet.contains(adTypeValue)) {
                acc + 1
            } else {
                acc
            }
        }
    }

    override suspend fun sumRevenue(fromTime: Long, toTime: Long): Double {
        val reports = executor.fetchReports(RoomBackedPolicyState.revenueQueryStart(fromTime), toTime)
        return reports.fold(0.0) { acc, row ->
            val revenueValue = row.revenue.toDoubleOrNull() ?: 0.0
            acc + revenueValue
        }
    }
}

/**
 * 统一管理 SQLDelight 查询的执行器，方便未来注入或测试替换。
 */
private class RevenueQueryExecutor {
    private suspend fun dao() = poseidonSetupProcessor().database().adExtraInfoDao()

    /**
     * 将业务记录写入数据库。
     */
    suspend fun persistRecord(record: AdRevenueRecord) {
        val payload = record.toInsertPayload()
        RoomPolicyDispatcher.run {
            dao().insert(
                AdExtraInfoEntity(
                    revenue = payload.revenue,
                    ecpm = payload.ecpm,
                    currencyCode = payload.currencyCode,
                    precision = payload.precision,
                    networkId = payload.networkId,
                    adSourceId = payload.adSourceId,
                    adSourcePlacementId = payload.adSourcePlacementId,
                    adUnitId = payload.adUnitId,
                    adType = payload.adType,
                    createTime = payload.createTime
                )
            )
        }
    }

    /**
     * 根据时间戳清理旧数据。
     */
    suspend fun prune(beforeTimestamp: Long) {
        RoomPolicyDispatcher.run {
            dao().deleteOldReports(beforeTimestamp)
        }
    }

    /**
     * 获取指定时间范围内的原始数据行。
     */
    suspend fun fetchReports(fromTime: Long, toTime: Long) =
        RoomPolicyDispatcher.run {
            dao().getReportsBetween(fromTime = fromTime, toTime = toTime)
        }
}

/**
 * 预处理后可直接入库的字段载体。
 */
private data class InsertPayload(
    val revenue: String,
    val ecpm: String,
    val currencyCode: String,
    val precision: Long,
    val networkId: Long,
    val adSourceId: Long,
    val adSourcePlacementId: String,
    val adUnitId: String,
    val adType: Long,
    val createTime: Long,
)

/**
 * 将模型转换为数据库写入参数，减少重复转换逻辑。
 */
private fun AdRevenueRecord.toInsertPayload() = InsertPayload(
    revenue = revenue.toString(),
    ecpm = eCPM.toString(),
    currencyCode = currencyCode,
    precision = precision.toLong(),
    networkId = networkId.toLong(),
    adSourceId = adSourceId.toLong(),
    adSourcePlacementId = adSourcePlacementId,
    adUnitId = adUnitId,
    adType = adType.toLong(),
    createTime = createTime
)
